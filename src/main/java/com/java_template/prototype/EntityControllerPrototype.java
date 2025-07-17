package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@Validated
@RequestMapping(path = "/prototype/bookings")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String RESTFUL_BOOKER_API_URL = "https://restful-booker.herokuapp.com/booking";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ReportSummary> lastReportCache = new ConcurrentHashMap<>();

    @PostMapping("/filter")
    public ResponseEntity<FilterResponse> filterBookings(@RequestBody @Valid FilterRequest filterRequest) {
        logger.info("Received filter request: {}", filterRequest);
        JsonNode bookingsNode;
        try {
            bookingsNode = restTemplate.getForObject(RESTFUL_BOOKER_API_URL, JsonNode.class);
        } catch (Exception e) {
            logger.error("Failed to fetch bookings from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch bookings from external API");
        }
        if (bookingsNode == null || !bookingsNode.isArray()) {
            logger.error("Unexpected response structure from external API");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid bookings data received");
        }
        List<Integer> bookingIds = new ArrayList<>();
        bookingsNode.forEach(node -> {
            if (node.has("bookingid")) {
                bookingIds.add(node.get("bookingid").asInt());
            }
        });
        List<Booking> detailedBookings = new ArrayList<>();
        for (Integer bookingId : bookingIds) {
            try {
                String url = RESTFUL_BOOKER_API_URL + "/" + bookingId;
                JsonNode bookingDetailNode = restTemplate.getForObject(url, JsonNode.class);
                if (bookingDetailNode != null) {
                    detailedBookings.add(parseBooking(bookingDetailNode, bookingId));
                }
            } catch (Exception e) {
                logger.error("Failed to fetch booking details for id {}", bookingId, e);
            }
        }
        List<Booking> filtered = detailedBookings.stream()
                .filter(b -> filterByDateRange(b, filterRequest.getDateFrom(), filterRequest.getDateTo()))
                .filter(b -> filterByPriceRange(b, filterRequest.getMinTotalPrice(), filterRequest.getMaxTotalPrice()))
                .filter(b -> filterByDepositPaid(b, filterRequest.isDepositPaid()))
                .collect(Collectors.toList());
        ReportSummary report = calculateReportSummary(filtered, filterRequest.getDateFrom(), filterRequest.getDateTo());
        lastReportCache.put("latest", report);
        FilterResponse response = new FilterResponse();
        response.setFilteredBookings(filtered);
        response.setReport(report);
        logger.info("Filter processing completed. Bookings filtered: {}, Report: {}", filtered.size(), report);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/latest")
    public ResponseEntity<ReportSummary> getLastReport() {
        ReportSummary report = lastReportCache.get("latest");
        if (report == null) {
            logger.info("No cached report found");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No cached report available");
        }
        logger.info("Returning cached report: {}", report);
        return ResponseEntity.ok(report);
    }

    private Booking parseBooking(JsonNode node, int bookingId) {
        Booking b = new Booking();
        b.setBookingId(bookingId);
        b.setFirstName(node.path("firstname").asText(""));
        b.setLastName(node.path("lastname").asText(""));
        b.setTotalPrice(node.path("totalprice").asInt(0));
        b.setDepositPaid(node.path("depositpaid").asBoolean(false));
        JsonNode dates = node.path("bookingdates");
        if (dates.isObject()) {
            b.setCheckin(dates.path("checkin").asText(null));
            b.setCheckout(dates.path("checkout").asText(null));
        }
        return b;
    }

    private boolean filterByDateRange(Booking b, String dateFrom, String dateTo) {
        if (dateFrom == null && dateTo == null) return true;
        try {
            LocalDate ci = b.getCheckin() != null ? LocalDate.parse(b.getCheckin()) : null;
            LocalDate co = b.getCheckout() != null ? LocalDate.parse(b.getCheckout()) : null;
            if (ci == null || co == null) return false;
            if (dateFrom != null && co.isBefore(LocalDate.parse(dateFrom))) return false;
            if (dateTo != null && ci.isAfter(LocalDate.parse(dateTo))) return false;
            return true;
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format in booking or filter", e);
            return false;
        }
    }

    private boolean filterByPriceRange(Booking b, int minPrice, int maxPrice) {
        int price = b.getTotalPrice();
        if (minPrice > 0 && price < minPrice) return false;
        if (maxPrice > 0 && price > maxPrice) return false;
        return true;
    }

    private boolean filterByDepositPaid(Booking b, boolean depositPaid) {
        return !depositPaid || b.isDepositPaid();
    }

    private ReportSummary calculateReportSummary(List<Booking> list, String dateFrom, String dateTo) {
        int total = list.stream().mapToInt(Booking::getTotalPrice).sum();
        double avg = list.isEmpty() ? 0 : (double) total / list.size();
        ReportSummary r = new ReportSummary();
        r.setTotalRevenue(total);
        r.setAverageBookingPrice(avg);
        r.setBookingCount(list.size());
        r.setDateFrom(dateFrom);
        r.setDateTo(dateTo);
        return r;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @Data
    public static class FilterRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "dateFrom must be in YYYY-MM-DD")
        private String dateFrom;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "dateTo must be in YYYY-MM-DD")
        private String dateTo;
        @Min(value = 0, message = "minTotalPrice must be >= 0")
        private int minTotalPrice;
        @Min(value = 0, message = "maxTotalPrice must be >= 0")
        private int maxTotalPrice;
        private boolean depositPaid;
    }

    @Data
    public static class FilterResponse {
        private List<Booking> filteredBookings;
        private ReportSummary report;
    }

    @Data
    public static class Booking {
        private int bookingId;
        private String firstName;
        private String lastName;
        private int totalPrice;
        private boolean depositPaid;
        private String checkin;
        private String checkout;
    }

    @Data
    public static class ReportSummary {
        private int totalRevenue;
        private double averageBookingPrice;
        private int bookingCount;
        private String dateFrom;
        private String dateTo;
    }
}