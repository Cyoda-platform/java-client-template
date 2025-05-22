package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/bookings")
public class EntityControllerPrototype {

    private static final String BOOKER_API_BOOKINGS_URL = "https://restful-booker.herokuapp.com/booking";
    private static final String BOOKER_API_BOOKING_DETAIL_URL = "https://restful-booker.herokuapp.com/booking/{id}";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ReportData> reportsCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/filter-report") // must be first
    public ResponseEntity<ReportData> filterAndReport(@RequestBody @Valid FilterRequest filterRequest) {
        log.info("Received filter-report request: {}", filterRequest);
        try {
            JsonNode bookingIdsNode = restTemplate.getForObject(new URI(BOOKER_API_BOOKINGS_URL), JsonNode.class);
            if (bookingIdsNode == null || !bookingIdsNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve bookings");
            }
            List<Integer> bookingIds = new ArrayList<>();
            for (JsonNode node : bookingIdsNode) {
                if (node.has("bookingid")) bookingIds.add(node.get("bookingid").asInt());
                else if (node.has("id")) bookingIds.add(node.get("id").asInt());
            }
            List<Booking> allBookings = new ArrayList<>();
            for (Integer id : bookingIds) {
                try {
                    JsonNode bookingDetailNode = restTemplate.getForObject(BOOKER_API_BOOKING_DETAIL_URL, JsonNode.class, id);
                    if (bookingDetailNode != null) {
                        allBookings.add(parseBooking(id, bookingDetailNode));
                    }
                } catch (Exception ex) {
                    log.error("Failed to fetch booking detail for id={} : {}", id, ex.getMessage());
                }
            }
            List<Booking> filtered = filterBookings(allBookings, filterRequest);
            ReportData reportData = calculateReport(filtered, filterRequest);
            String reportId = UUID.randomUUID().toString();
            reportsCache.put(reportId, reportData);
            reportData.setReportId(reportId);
            return ResponseEntity.ok(reportData);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Unexpected error", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }

    @GetMapping("/report/{reportId}") // must be first
    public ResponseEntity<ReportData> getReport(@PathVariable @NotBlank String reportId) {
        ReportData reportData = reportsCache.get(reportId);
        if (reportData == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseEntity.ok(reportData);
    }

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
            log.warn("Error parsing dates id={}: {}", id, e.getMessage());
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
        if (!StringUtils.hasText(dateStr)) return null;
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String,Object> err = new HashMap<>();
        err.put("status", ex.getStatusCode().value());
        err.put("error", ex.getStatusCode().getReasonPhrase());
        err.put("message", ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterRequest {
        @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}", message="dateFrom must be YYYY-MM-DD")
        private String dateFrom;
        @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}", message="dateTo must be YYYY-MM-DD")
        private String dateTo;
        @Min(value=0, message="minTotalPrice must be >= 0")
        private Double minTotalPrice;
        @Min(value=0, message="maxTotalPrice must be >= 0")
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