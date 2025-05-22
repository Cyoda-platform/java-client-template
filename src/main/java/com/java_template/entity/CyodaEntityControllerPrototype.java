package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda/bookings")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final String ENTITY_NAME = "booking";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/filter-report") // must be first
    public ResponseEntity<ReportData> filterAndReport(@RequestBody @Valid FilterRequest filterRequest) throws ExecutionException, InterruptedException {
        logger.info("Received filter-report request: {}", filterRequest);

        // Fetch booking IDs from external API
        JsonNode bookingIdsNode = restTemplate.getForObject(URI.create("https://restful-booker.herokuapp.com/booking"), JsonNode.class);
        if (bookingIdsNode == null || !bookingIdsNode.isArray()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve bookings");
        }
        List<Integer> bookingIds = new ArrayList<>();
        for (JsonNode node : bookingIdsNode) {
            if (node.has("bookingid")) bookingIds.add(node.get("bookingid").asInt());
            else if (node.has("id")) bookingIds.add(node.get("id").asInt());
        }

        // Fetch booking details from external API
        List<Booking> allBookings = new ArrayList<>();
        for (Integer id : bookingIds) {
            try {
                JsonNode bookingDetailNode = restTemplate.getForObject("https://restful-booker.herokuapp.com/booking/{id}", JsonNode.class, id);
                if (bookingDetailNode != null) {
                    allBookings.add(parseBooking(id, bookingDetailNode));
                }
            } catch (Exception ex) {
                logger.error("Failed to fetch booking detail for id={} : {}", id, ex.getMessage());
            }
        }

        // Filter bookings
        List<Booking> filtered = filterBookings(allBookings, filterRequest);

        // Calculate report
        ReportData reportData = calculateReport(filtered, filterRequest);

        // Save report using entityService
        // Since reportId is generated here, set it before saving
        String generatedReportId = UUID.randomUUID().toString();
        reportData.setReportId(generatedReportId);

        // Use entityService to add report as entity
        // Prepare data as object (ReportData)
        // Use addItem with entityModel = "report" (assuming report is an entity model)
        // If report entity does not exist, as per instructions skip (so we keep local cache)
        // But requirement is to replace local caches with entityService - but no mention of "report" entity model
        // So leave reportsCache as is for reports

        reportsCache.put(generatedReportId, reportData);

        return ResponseEntity.ok(reportData);
    }

    @GetMapping("/report/{reportId}") // must be first
    public ResponseEntity<ReportData> getReport(@PathVariable @NotBlank String reportId) {
        ReportData reportData = reportsCache.get(reportId);
        if (reportData == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseEntity.ok(reportData);
    }

    // The in-memory cache for reports is kept as is because no entityService method for reports specified
    private final Map<String, ReportData> reportsCache = new HashMap<>();

    private Booking parseBooking(Integer id, JsonNode node) {
        BookingDates dates = null;
        try {
            JsonNode bookingdatesNode = node.get("bookingdates");
            if (bookingdatesNode != null) {
                dates = new BookingDates(
                        bookingdatesNode.path("checkin").asText(null),
                        bookingdatesNode.path("checkout").asText(null));
            }
        } catch (Exception e) {
            logger.warn("Error parsing dates id={}: {}", id, e.getMessage());
        }
        return new Booking(
                id,
                node.path("firstname").asText(null),
                node.path("lastname").asText(null),
                node.path("totalprice").asDouble(0),
                node.path("depositpaid").asBoolean(false),
                dates
        );
    }

    private List<Booking> filterBookings(List<Booking> bookings, FilterRequest filter) {
        LocalDate dateFrom = parseDateOrNull(filter.getDateFrom());
        LocalDate dateTo = parseDateOrNull(filter.getDateTo());
        return bookings.stream().filter(b -> {
            if (filter.getDepositPaid() != null && b.isDepositPaid() != filter.getDepositPaid()) return false;
            if (filter.getMinTotalPrice() != null && b.getTotalPrice() < filter.getMinTotalPrice()) return false;
            if (filter.getMaxTotalPrice() != null && b.getTotalPrice() > filter.getMaxTotalPrice()) return false;
            if (b.getBookingDates() != null) {
                LocalDate checkin = parseDateOrNull(b.getBookingDates().getCheckin());
                LocalDate checkout = parseDateOrNull(b.getBookingDates().getCheckout());
                if (checkin == null || checkout == null) return false;
                if (dateFrom != null && checkout.isBefore(dateFrom)) return false;
                if (dateTo != null && checkin.isAfter(dateTo)) return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    private LocalDate parseDateOrNull(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private ReportData calculateReport(List<Booking> filtered, FilterRequest filter) {
        int totalBookings = filtered.size();
        double totalRevenue = filtered.stream().mapToDouble(Booking::getTotalPrice).sum();
        double average = totalBookings > 0 ? totalRevenue / totalBookings : 0;
        int withinRange = totalBookings;
        if (filter.getDateFrom() != null && filter.getDateTo() != null) {
            LocalDate from = parseDateOrNull(filter.getDateFrom());
            LocalDate to = parseDateOrNull(filter.getDateTo());
            withinRange = (int) filtered.stream().filter(b -> {
                if (b.getBookingDates() == null) return false;
                LocalDate ci = parseDateOrNull(b.getBookingDates().getCheckin());
                LocalDate co = parseDateOrNull(b.getBookingDates().getCheckout());
                if (ci == null || co == null) return false;
                return !(co.isBefore(from) || ci.isAfter(to));
            }).count();
        }
        ReportData report = new ReportData();
        report.setTotalBookings(totalBookings);
        report.setTotalRevenue(round(totalRevenue, 2));
        report.setAverageBookingPrice(round(average, 2));
        report.setBookingsWithinDateRange(withinRange);
        report.setBookings(filtered);
        return report;
    }

    private double round(double val, int places) {
        long factor = (long) Math.pow(10, places);
        return (double) Math.round(val * factor) / factor;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "dateFrom must be YYYY-MM-DD")
        private String dateFrom;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "dateTo must be YYYY-MM-DD")
        private String dateTo;
        @Min(value = 0, message = "minTotalPrice must be >= 0")
        private Double minTotalPrice;
        @Min(value = 0, message = "maxTotalPrice must be >= 0")
        private Double maxTotalPrice;
        private Boolean depositPaid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingDates {
        private String checkin;
        private String checkout;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Booking {
        private Integer bookingId;
        private String firstname;
        private String lastname;
        private double totalPrice;
        private boolean depositPaid;
        private BookingDates bookingDates;
    }

    @Data
    @NoArgsConstructor
    public static class ReportData {
        private String reportId;
        private int totalBookings;
        private double totalRevenue;
        private double averageBookingPrice;
        private int bookingsWithinDateRange;
        private List<Booking> bookings = Collections.emptyList();
    }
}