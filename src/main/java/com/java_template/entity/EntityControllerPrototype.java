package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Validated
@RestController
@RequestMapping("/entity")
public class EntityControllerPrototype {

    private static final String BASE_API_URL = "https://restful-booker.herokuapp.com";
    private static final String BOOKING_IDS_ENDPOINT = BASE_API_URL + "/booking";
    private static final String BOOKING_DETAILS_ENDPOINT_TEMPLATE = BASE_API_URL + "/booking/%d";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, BookingReportResponse> reportsCache = new ConcurrentHashMap<>();
    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @PostMapping("/bookings/filter") // must be first
    public ResponseEntity<BookingReportResponse> filterBookings(@RequestBody @Valid BookingFilterRequest request) {
        log.info("Received filter request: {}", request);
        try {
            ResponseEntity<String> bookingIdsResp = restTemplate.getForEntity(BOOKING_IDS_ENDPOINT, String.class);
            if (!bookingIdsResp.getStatusCode().is2xxSuccessful() || bookingIdsResp.getBody() == null) {
                log.error("Failed to fetch booking IDs, status: {}", bookingIdsResp.getStatusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch booking IDs");
            }
            JsonNode bookingIdsJson = objectMapper.readTree(bookingIdsResp.getBody());
            List<Integer> bookingIds = new ArrayList<>();
            if (bookingIdsJson.isArray()) {
                for (JsonNode node : bookingIdsJson) {
                    bookingIds.add(node.get("bookingid").asInt());
                }
            }
            List<BookingDetails> allBookings = new ArrayList<>();
            for (Integer id : bookingIds) {
                String url = String.format(BOOKING_DETAILS_ENDPOINT_TEMPLATE, id);
                try {
                    ResponseEntity<String> bookingResp = restTemplate.getForEntity(url, String.class);
                    if (!bookingResp.getStatusCode().is2xxSuccessful() || bookingResp.getBody() == null) {
                        log.warn("Skipping booking {} due to fetch failure: {}", id, bookingResp.getStatusCode());
                        continue;
                    }
                    JsonNode bookingJson = objectMapper.readTree(bookingResp.getBody());
                    BookingDetails bd = parseBookingDetails(id, bookingJson);
                    allBookings.add(bd);
                } catch (Exception e) {
                    log.warn("Exception fetching booking {}: {}", id, e.getMessage());
                }
            }
            List<BookingDetails> filtered = allBookings.stream()
                    .filter(b -> filterBooking(b, request.getFilters()))
                    .collect(Collectors.toList());
            BookingSummary summary = calculateSummary(filtered, request.getReportDateRanges());
            BookingReportResponse response = new BookingReportResponse(filtered, summary);
            reportsCache.put("latest", response);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Unexpected error during filterBookings", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        }
    }

    @GetMapping("/reports/latest") // must be first
    public ResponseEntity<BookingReportResponse> getLatestReport() {
        BookingReportResponse report = reportsCache.get("latest");
        if (report == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(report);
    }

    private BookingDetails parseBookingDetails(int bookingId, JsonNode json) {
        BookingDetails bd = new BookingDetails();
        bd.setBookingId(bookingId);
        bd.setFirstName(json.path("firstname").asText(null));
        bd.setLastName(json.path("lastname").asText(null));
        bd.setTotalPrice(json.path("totalprice").asDouble(0));
        bd.setDepositPaid(json.path("depositpaid").asBoolean(false));
        JsonNode bookingDates = json.path("bookingdates");
        if (!bookingDates.isMissingNode()) {
            BookingDates dates = new BookingDates();
            dates.setCheckin(bookingDates.path("checkin").asText(null));
            dates.setCheckout(bookingDates.path("checkout").asText(null));
            bd.setBookingDates(dates);
        }
        return bd;
    }

    private boolean filterBooking(BookingDetails booking, BookingFilter filters) {
        if (filters == null) return true;
        if (filters.getCheckinDateFrom() != null) {
            LocalDate from = LocalDate.parse(filters.getCheckinDateFrom(), DATE_FORMATTER);
            LocalDate checkin = safeParseDate(booking.getBookingDates() != null ? booking.getBookingDates().getCheckin() : null);
            if (checkin == null || checkin.isBefore(from)) return false;
        }
        if (filters.getCheckinDateTo() != null) {
            LocalDate to = LocalDate.parse(filters.getCheckinDateTo(), DATE_FORMATTER);
            LocalDate checkin = safeParseDate(booking.getBookingDates() != null ? booking.getBookingDates().getCheckin() : null);
            if (checkin == null || checkin.isAfter(to)) return false;
        }
        if (filters.getTotalPriceMin() != null && booking.getTotalPrice() < filters.getTotalPriceMin()) return false;
        if (filters.getTotalPriceMax() != null && booking.getTotalPrice() > filters.getTotalPriceMax()) return false;
        if (filters.getDepositPaid() != null && !filters.getDepositPaid().equals(booking.getDepositPaid())) return false;
        return true;
    }

    private LocalDate safeParseDate(String dateStr) {
        try {
            return dateStr == null ? null : LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private BookingSummary calculateSummary(List<BookingDetails> bookings, List<DateRange> dateRanges) {
        double totalRevenue = bookings.stream().mapToDouble(BookingDetails::getTotalPrice).sum();
        double avgPrice = bookings.isEmpty() ? 0 : totalRevenue / bookings.size();
        List<DateRangeCount> counts = new ArrayList<>();
        if (dateRanges != null) {
            for (DateRange dr : dateRanges) {
                LocalDate from = safeParseDate(dr.getFrom());
                LocalDate to = safeParseDate(dr.getTo());
                if (from == null || to == null) continue;
                long count = bookings.stream()
                        .filter(b -> {
                            LocalDate checkin = safeParseDate(b.getBookingDates() != null ? b.getBookingDates().getCheckin() : null);
                            return checkin != null && !checkin.isBefore(from) && !checkin.isAfter(to);
                        }).count();
                counts.add(new DateRangeCount(dr.getFrom(), dr.getTo(), count));
            }
        }
        return new BookingSummary(totalRevenue, avgPrice, counts);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @Data
    public static class BookingFilterRequest {
        @Valid
        private BookingFilter filters;
        @Valid
        private List<@Valid DateRange> reportDateRanges;
    }

    @Data
    public static class BookingFilter {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String checkinDateFrom;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String checkinDateTo;
        @Min(0)
        private Double totalPriceMin;
        @Min(0)
        private Double totalPriceMax;
        @NotNull
        private Boolean depositPaid;
    }

    @Data
    public static class DateRange {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String from;
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String to;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BookingReportResponse {
        private List<BookingDetails> filteredBookings;
        private BookingSummary summary;
    }

    @Data
    public static class BookingDetails {
        private int bookingId;
        private String firstName;
        private String lastName;
        private double totalPrice;
        private boolean depositPaid;
        private BookingDates bookingDates;
    }

    @Data
    public static class BookingDates {
        private String checkin;
        private String checkout;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BookingSummary {
        private double totalRevenue;
        private double averageBookingPrice;
        private List<DateRangeCount> bookingsCountByDateRange;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DateRangeCount {
        private String from;
        private String to;
        private long count;
    }
}