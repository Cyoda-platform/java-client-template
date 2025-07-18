package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@Validated
@RequestMapping(path = "/prototype/bookings")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private static final String RESTFUL_BOOKER_API_URL = "https://restful-booker.herokuapp.com/booking";
    private static final String ENTITY_NAME = "Booking";

    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, ReportSummary> lastReportCache = new HashMap<>();

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/filter")
    public ResponseEntity<FilterResponse> filterBookings(@RequestBody @Valid FilterRequest filterRequest) {
        logger.info("Received filter request: {}", filterRequest);

        JsonNode bookingsNode;
        try {
            bookingsNode = restTemplate.getForObject(RESTFUL_BOOKER_API_URL, JsonNode.class);
        } catch (Exception e) {
            logger.error("Failed to fetch bookings from external API", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to fetch bookings from external API");
        }
        if (bookingsNode == null || !bookingsNode.isArray()) {
            logger.error("Unexpected response structure from external API");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Invalid bookings data received");
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

        // Removed business logic for filtering; processors handle business logic now
        // But we keep the persistence call as required
        // Persisting all fetched detailedBookings (example of addItem call)
        try {
            for (Booking b : detailedBookings) {
                CompletableFuture<UUID> addFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, b);
                addFuture.get(); // Wait for persistence before processing
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error persisting bookings", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error persisting bookings");
        }

        // Just returning the fetched and parsed bookings without filtering or report calculations here
        FilterResponse response = new FilterResponse();
        response.setFilteredBookings(detailedBookings);
        response.setReport(null); // No report generated here, moved to processors

        logger.info("Filter processing completed. Bookings fetched: {}", detailedBookings.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/latest")
    public ResponseEntity<ReportSummary> getLastReport() {
        ReportSummary report = lastReportCache.get("latest");
        if (report == null) {
            logger.info("No cached report found");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No cached report available");
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    // DTO classes can be moved to separate files if needed; kept here for simplicity

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

        public String getDateFrom() {return dateFrom;}
        public void setDateFrom(String dateFrom) {this.dateFrom = dateFrom;}
        public String getDateTo() {return dateTo;}
        public void setDateTo(String dateTo) {this.dateTo = dateTo;}
        public int getMinTotalPrice() {return minTotalPrice;}
        public void setMinTotalPrice(int minTotalPrice) {this.minTotalPrice = minTotalPrice;}
        public int getMaxTotalPrice() {return maxTotalPrice;}
        public void setMaxTotalPrice(int maxTotalPrice) {this.maxTotalPrice = maxTotalPrice;}
        public boolean isDepositPaid() {return depositPaid;}
        public void setDepositPaid(boolean depositPaid) {this.depositPaid = depositPaid;}
    }

    public static class FilterResponse {
        private List<Booking> filteredBookings;
        private ReportSummary report;

        public List<Booking> getFilteredBookings() {return filteredBookings;}
        public void setFilteredBookings(List<Booking> filteredBookings) {this.filteredBookings = filteredBookings;}
        public ReportSummary getReport() {return report;}
        public void setReport(ReportSummary report) {this.report = report;}
    }

    public static class Booking {
        private int bookingId;
        private String firstName;
        private String lastName;
        private int totalPrice;
        private boolean depositPaid;
        private String checkin;
        private String checkout;

        public int getBookingId() {return bookingId;}
        public void setBookingId(int bookingId) {this.bookingId = bookingId;}
        public String getFirstName() {return firstName;}
        public void setFirstName(String firstName) {this.firstName = firstName;}
        public String getLastName() {return lastName;}
        public void setLastName(String lastName) {this.lastName = lastName;}
        public int getTotalPrice() {return totalPrice;}
        public void setTotalPrice(int totalPrice) {this.totalPrice = totalPrice;}
        public boolean isDepositPaid() {return depositPaid;}
        public void setDepositPaid(boolean depositPaid) {this.depositPaid = depositPaid;}
        public String getCheckin() {return checkin;}
        public void setCheckin(String checkin) {this.checkin = checkin;}
        public String getCheckout() {return checkout;}
        public void setCheckout(String checkout) {this.checkout = checkout;}
    }

    public static class ReportSummary {
        private int totalRevenue;
        private double averageBookingPrice;
        private int bookingCount;
        private String dateFrom;
        private String dateTo;

        public int getTotalRevenue() {return totalRevenue;}
        public void setTotalRevenue(int totalRevenue) {this.totalRevenue = totalRevenue;}
        public double getAverageBookingPrice() {return averageBookingPrice;}
        public void setAverageBookingPrice(double averageBookingPrice) {this.averageBookingPrice = averageBookingPrice;}
        public int getBookingCount() {return bookingCount;}
        public void setBookingCount(int bookingCount) {this.bookingCount = bookingCount;}
        public String getDateFrom() {return dateFrom;}
        public void setDateFrom(String dateFrom) {this.dateFrom = dateFrom;}
        public String getDateTo() {return dateTo;}
        public void setDateTo(String dateTo) {this.dateTo = dateTo;}
    }
}