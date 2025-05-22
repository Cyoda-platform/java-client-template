```java
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
public class EntityControllerPrototype {

    private static final String BOOKER_API_BOOKINGS_URL = "https://restful-booker.herokuapp.com/booking";
    private static final String BOOKER_API_BOOKING_DETAIL_URL = "https://restful-booker.herokuapp.com/booking/{id}";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // For caching or storing generated reports by UUID
    private final Map<String, ReportData> reportsCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    /**
     * POST /api/bookings/filter-report
     * Retrieves all bookings from external API, filters them, calculates report data,
     * and returns the report along with filtered booking details.
     */
    @PostMapping("/filter-report")
    public ResponseEntity<ReportData> filterAndReport(@RequestBody FilterRequest filterRequest) {
        log.info("Received filter-report request: {}", filterRequest);

        try {
            // 1. Fetch all booking IDs from external API
            JsonNode bookingIdsNode = restTemplate.getForObject(new URI(BOOKER_API_BOOKINGS_URL), JsonNode.class);
            if (bookingIdsNode == null || !bookingIdsNode.isArray()) {
                log.error("Unexpected response format fetching booking IDs");
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve bookings");
            }

            List<Integer> bookingIds = new ArrayList<>();
            for (JsonNode node : bookingIdsNode) {
                if (node.has("bookingid")) {
                    bookingIds.add(node.get("bookingid").asInt());
                } else if (node.has("id")) { // fallback if API changes
                    bookingIds.add(node.get("id").asInt());
                }
            }
            log.info("Fetched {} booking IDs", bookingIds.size());

            // 2. Fetch full booking details in batch (sequentially here - TODO: optimize with async calls)
            List<Booking> allBookings = new ArrayList<>();
            for (Integer id : bookingIds) {
                try {
                    JsonNode bookingDetailNode = restTemplate.getForObject(BOOKER_API_BOOKING_DETAIL_URL, JsonNode.class, id);
                    if (bookingDetailNode != null) {
                        Booking booking = parseBooking(id, bookingDetailNode);
                        allBookings.add(booking);
                    }
                } catch (Exception ex) {
                    log.error("Failed to fetch booking detail for id={} : {}", id, ex.getMessage());
                    // continue fetching others
                }
            }
            log.info("Fetched detailed info for {} bookings", allBookings.size());

            // 3. Filter bookings by criteria
            List<Booking> filtered = filterBookings(allBookings, filterRequest);

            // 4. Calculate report data
            ReportData reportData = calculateReport(filtered, filterRequest);

            // 5. Cache report with UUID (optional)
            String reportId = UUID.randomUUID().toString();
            reportsCache.put(reportId, reportData);
            reportData.setReportId(reportId);

            log.info("Report generated with id={}", reportId);

            return ResponseEntity.ok(reportData);

        } catch (ResponseStatusException rse) {
            log.error("Error processing filter-report request: {}", rse.getReason());
            throw rse;
        } catch (Exception e) {
            log.error("Unexpected error processing filter-report request", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }

    /**
     * GET /api/bookings/report/{reportId}
     * Returns a previously generated report from cache.
     */
    @GetMapping("/report/{reportId}")
    public ResponseEntity<ReportData> getReport(@PathVariable String reportId) {
        log.info("Fetching report with id={}", reportId);
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
            log.warn("Error parsing booking dates for booking id={}: {}", id, e.getMessage());
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
        return bookings.stream()
                .filter(b -> {
                    // Filter by depositPaid if set
                    if (filter.getDepositPaid() != null && b.isDepositPaid() != filter.getDepositPaid()) {
                        return false;
                    }
                    // Filter by total price min/max
                    if (filter.getMinTotalPrice() != null && b.getTotalPrice() < filter.getMinTotalPrice()) {
                        return false;
                    }
                    if (filter.getMaxTotalPrice() != null && b.getTotalPrice() > filter.getMaxTotalPrice()) {
                        return false;
                    }
                    // Filter by date range if booking dates exist
                    if (b.getBookingDates() != null) {
                        LocalDate checkin = parseDateOrNull(b.getBookingDates().getCheckin());
                        LocalDate checkout = parseDateOrNull(b.getBookingDates().getCheckout());
                        if (checkin == null || checkout == null) {
                            return false; // exclude if dates missing
                        }
                        if (filter.getDateFrom() != null && checkout.isBefore(filter.getDateFrom())) {
                            return false;
                        }
                        if (filter.getDateTo() != null && checkin.isAfter(filter.getDateTo())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
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
        double averageBookingPrice = totalBookings > 0 ? totalRevenue / totalBookings : 0;

        // Count bookings within date range (if specified)
        int bookingsWithinDateRange = 0;
        if (filter.getDateFrom() != null && filter.getDateTo() != null) {
            bookingsWithinDateRange = (int) filtered.stream()
                    .filter(b -> {
                        if (b.getBookingDates() == null) return false;
                        LocalDate checkin = parseDateOrNull(b.getBookingDates().getCheckin());
                        LocalDate checkout = parseDateOrNull(b.getBookingDates().getCheckout());
                        if (checkin == null || checkout == null) return false;
                        return !(checkout.isBefore(filter.getDateFrom()) || checkin.isAfter(filter.getDateTo()));
                    })
                    .count();
        } else {
            bookingsWithinDateRange = totalBookings;
        }

        ReportData report = new ReportData();
        report.setTotalBookings(totalBookings);
        report.setTotalRevenue(round(totalRevenue, 2));
        report.setAverageBookingPrice(round(averageBookingPrice, 2));
        report.setBookingsWithinDateRange(bookingsWithinDateRange);
        report.setBookings(filtered);

        return report;
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        return (double) Math.round(value * factor) / factor;
    }

    // --- Exception Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }


    // --- DTOs and Data Classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterRequest {
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private Double minTotalPrice;
        private Double maxTotalPrice;
        private Boolean depositPaid;
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
    @AllArgsConstructor
    public static class BookingDates {
        private String checkin;
        private String checkout;
    }

    @Data
    @NoArgsConstructor
    public static class ReportData {
        private String reportId; // assigned UUID for caching
        private int totalBookings;
        private double totalRevenue;
        private double averageBookingPrice;
        private int bookingsWithinDateRange;
        private List<Booking> bookings = Collections.emptyList();
    }
}
```
