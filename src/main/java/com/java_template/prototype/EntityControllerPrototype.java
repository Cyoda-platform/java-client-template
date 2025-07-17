package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
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
@RequestMapping(path = "/prototype/bookings")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private static final String RESTFUL_BOOKER_API_URL = "https://restful-booker.herokuapp.com/booking";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, ReportSummary> lastReportCache = new ConcurrentHashMap<>();

    /**
     * POST /prototype/bookings/filter
     * Fetches all bookings from the external API, applies filtering,
     * calculates report summary, caches report, and returns filtered bookings + report.
     */
    @PostMapping("/filter")
    public ResponseEntity<FilterResponse> filterBookings(@Valid @RequestBody FilterRequest filterRequest) {
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

        // bookingsNode is an array of objects with "bookingid" field
        List<Integer> bookingIds = new ArrayList<>();
        bookingsNode.forEach(node -> {
            if (node.has("bookingid")) {
                bookingIds.add(node.get("bookingid").asInt());
            }
        });

        // Fetch detailed bookings info in batch (one by one here due to API limitations)
        List<Booking> detailedBookings = new ArrayList<>();
        for (Integer bookingId : bookingIds) {
            try {
                String url = RESTFUL_BOOKER_API_URL + "/" + bookingId;
                JsonNode bookingDetailNode = restTemplate.getForObject(url, JsonNode.class);
                if (bookingDetailNode != null) {
                    Booking booking = parseBooking(bookingDetailNode, bookingId);
                    detailedBookings.add(booking);
                }
            } catch (Exception e) {
                logger.error("Failed to fetch booking details for id {}", bookingId, e);
                // Skip this booking, continue others
            }
        }

        // Apply filters
        List<Booking> filtered = detailedBookings.stream()
                .filter(b -> filterByDateRange(b, filterRequest.dateFrom, filterRequest.dateTo))
                .filter(b -> filterByPriceRange(b, filterRequest.minTotalPrice, filterRequest.maxTotalPrice))
                .filter(b -> filterByDepositPaid(b, filterRequest.depositPaid))
                .collect(Collectors.toList());

        // Calculate report summary
        ReportSummary report = calculateReportSummary(filtered, filterRequest.dateFrom, filterRequest.dateTo);

        // Cache report keyed by a constant key (only one cached report)
        lastReportCache.put("latest", report);

        FilterResponse response = new FilterResponse();
        response.setFilteredBookings(filtered);
        response.setReport(report);

        logger.info("Filter processing completed. Bookings filtered: {}, Report: {}", filtered.size(), report);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /prototype/bookings/reports/latest
     * Returns the last cached report summary or 404 if none available.
     */
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

    // --- Parsing and filtering helpers ---

    private Booking parseBooking(JsonNode node, int bookingId) {
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setFirstName(node.path("firstname").asText(""));
        booking.setLastName(node.path("lastname").asText(""));
        booking.setTotalPrice(node.path("totalprice").asInt(0));
        booking.setDepositPaid(node.path("depositpaid").asBoolean(false));
        JsonNode bookingDatesNode = node.path("bookingdates");
        if (bookingDatesNode.isObject()) {
            booking.setCheckin(bookingDatesNode.path("checkin").asText(null));
            booking.setCheckout(bookingDatesNode.path("checkout").asText(null));
        }
        return booking;
    }

    private boolean filterByDateRange(Booking booking, String dateFrom, String dateTo) {
        if (dateFrom == null && dateTo == null) {
            return true;
        }
        try {
            LocalDate checkin = booking.getCheckin() != null ? LocalDate.parse(booking.getCheckin()) : null;
            LocalDate checkout = booking.getCheckout() != null ? LocalDate.parse(booking.getCheckout()) : null;

            if (checkin == null || checkout == null) {
                return false;
            }
            if (dateFrom != null) {
                LocalDate from = LocalDate.parse(dateFrom);
                if (checkout.isBefore(from)) {
                    return false;
                }
            }
            if (dateTo != null) {
                LocalDate to = LocalDate.parse(dateTo);
                if (checkin.isAfter(to)) {
                    return false;
                }
            }
            return true;
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format in booking or filter", e);
            return false;
        }
    }

    private boolean filterByPriceRange(Booking booking, Integer minPrice, Integer maxPrice) {
        int price = booking.getTotalPrice();
        if (minPrice != null && price < minPrice) {
            return false;
        }
        if (maxPrice != null && price > maxPrice) {
            return false;
        }
        return true;
    }

    private boolean filterByDepositPaid(Booking booking, Boolean depositPaid) {
        if (depositPaid == null) {
            return true;
        }
        return depositPaid.equals(booking.isDepositPaid());
    }

    private ReportSummary calculateReportSummary(List<Booking> bookings, String dateFrom, String dateTo) {
        int totalRevenue = bookings.stream().mapToInt(Booking::getTotalPrice).sum();
        double averageBookingPrice = bookings.isEmpty() ? 0 : (double) totalRevenue / bookings.size();
        ReportSummary summary = new ReportSummary();
        summary.setTotalRevenue(totalRevenue);
        summary.setAverageBookingPrice(averageBookingPrice);
        summary.setBookingCount(bookings.size());
        summary.setDateRange(new DateRange(dateFrom, dateTo));
        return summary;
    }

    // --- Exception handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    // --- DTO and Model classes ---

    @Data
    public static class FilterRequest {
        // Optional filters
        private String dateFrom;
        private String dateTo;
        private Integer minTotalPrice;
        private Integer maxTotalPrice;
        private Boolean depositPaid;
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
        private DateRange dateRange;
    }

    @Data
    public static class DateRange {
        private String from;
        private String to;

        public DateRange(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }
}