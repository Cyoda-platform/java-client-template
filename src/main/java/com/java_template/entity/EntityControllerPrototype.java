package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/prototype/api")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, Booking> bookings = new ConcurrentHashMap<>();

    @PostMapping("/bookings/retrieve") // must be first
    public ResponseEntity<JsonNode> retrieveBookings(@RequestBody @Valid ApiRequest request) {
        try {
            String apiUrl = "https://restful-booker.herokuapp.com/booking";
            ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);
            JsonNode bookingsNode = objectMapper.readTree(response.getBody());
            // TODO: Process and store bookings
            return ResponseEntity.ok(bookingsNode);
        } catch (Exception e) {
            logger.error("Error retrieving bookings", e);
            throw new ResponseStatusException(
                    ResponseStatusException.getStatusCode().value(), "Error retrieving bookings", e);
        }
    }

    @PostMapping("/bookings/filter") // must be first
    public ResponseEntity<JsonNode> filterBookings(@RequestBody @Valid FilterCriteria filterCriteria) {
        try {
            // TODO: Implement filtering logic
            JsonNode filteredBookings = objectMapper.createObjectNode(); // Placeholder
            return ResponseEntity.ok(filteredBookings);
        } catch (Exception e) {
            logger.error("Error filtering bookings", e);
            throw new ResponseStatusException(
                    ResponseStatusException.getStatusCode().value(), "Error filtering bookings", e);
        }
    }

    @PostMapping("/reports/generate") // must be first
    public ResponseEntity<ReportSummary> generateReport(@RequestBody @Valid ReportCriteria reportCriteria) {
        try {
            // TODO: Implement report generation logic
            ReportSummary reportSummary = new ReportSummary(1500, 200, 10); // Placeholder
            return ResponseEntity.ok(reportSummary);
        } catch (Exception e) {
            logger.error("Error generating report", e);
            throw new ResponseStatusException(
                    ResponseStatusException.getStatusCode().value(), "Error generating report", e);
        }
    }

    @GetMapping("/reports") // must be first
    public ResponseEntity<ReportSummary> getReport() {
        try {
            // TODO: Retrieve and return the generated report
            ReportSummary reportSummary = new ReportSummary(1500, 200, 10); // Placeholder
            return ResponseEntity.ok(reportSummary);
        } catch (Exception e) {
            logger.error("Error retrieving report", e);
            throw new ResponseStatusException(
                    ResponseStatusException.getStatusCode().value(), "Error retrieving report", e);
        }
    }

    // Mock Data Classes
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ApiRequest {
        @NotBlank
        private String apiKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FilterCriteria {
        @NotBlank
        private String startDate;
        
        @NotBlank
        private String endDate;

        @NotNull
        private Double minPrice;

        @NotNull
        private Double maxPrice;

        @NotNull
        private Boolean depositPaid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReportCriteria {
        @NotBlank
        private String startDate;

        @NotBlank
        private String endDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReportSummary {
        private int totalRevenue;
        private int averageBookingPrice;
        private int numberOfBookings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Booking {
        private int id;
        private String firstName;
        private String lastName;
        private int totalPrice;
        private boolean depositPaid;
        private BookingDates bookingDates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class BookingDates {
        private String checkIn;
        private String checkOut;
    }
}