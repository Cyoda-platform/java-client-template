```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/bookings")
    public CompletableFuture<ResponseEntity<JsonNode>> retrieveAllBookings() {
        return entityService.getItems("Booking", ENTITY_VERSION)
                .thenApply(bookings -> {
                    logger.info("Retrieved all bookings successfully.");
                    return ResponseEntity.ok(bookings);
                })
                .exceptionally(e -> {
                    logger.error("Error retrieving bookings", e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                });
    }

    @PostMapping("/bookings/filter")
    public CompletableFuture<ResponseEntity<JsonNode>> filterBookings(@RequestBody @Valid FilterCriteria filterCriteria) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.totalPrice", "GREATER_OR_EQUAL", filterCriteria.getMinPrice()),
                Condition.of("$.totalPrice", "LESS_OR_EQUAL", filterCriteria.getMaxPrice()),
                Condition.of("$.depositPaid", "EQUALS", filterCriteria.isDepositPaid())
        );

        return entityService.getItemsByCondition("Booking", ENTITY_VERSION, condition)
                .thenApply(filteredBookings -> {
                    logger.info("Filtered bookings based on criteria: {}", filterCriteria);
                    return ResponseEntity.ok(filteredBookings);
                })
                .exceptionally(e -> {
                    logger.error("Error filtering bookings", e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                });
    }

    @PostMapping("/reports")
    public CompletableFuture<ResponseEntity<Report>> generateReport(@RequestBody @Valid ReportCriteria reportCriteria) {
        return entityService.getItems("Booking", ENTITY_VERSION)
                .thenApply(bookings -> {
                    // Mocked report generation logic for demonstration
                    Report report = new Report(5000, 250, 20); // Replace with actual report logic
                    logger.info("Generated report with criteria: {}", reportCriteria);
                    return ResponseEntity.ok(report);
                })
                .exceptionally(e -> {
                    logger.error("Error generating report", e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleException(ResponseStatusException ex) {
        String errorMessage = "Error: " + ex.getStatusCode().toString();
        logger.error(errorMessage);
        return new ResponseEntity<>(errorMessage, ex.getStatusCode());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Booking {
        @JsonIgnore
        private UUID technicalId;
        private int id;
        @NotBlank
        private String firstName;
        @NotBlank
        private String lastName;
        @Positive
        private double totalPrice;
        private boolean depositPaid;
        @NotNull
        @Valid
        private BookingDates bookingDates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class BookingDates {
        @NotBlank
        private String checkIn;
        @NotBlank
        private String checkOut;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FilterCriteria {
        @Valid
        private BookingDates bookingDates;
        @PositiveOrZero
        private double minPrice;
        @Positive
        private double maxPrice;
        private boolean depositPaid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ReportCriteria {
        @Valid
        private BookingDates dateRange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Report {
        @Positive
        private double totalRevenue;
        @Positive
        private double averageBookingPrice;
        @Positive
        private int numberOfBookings;
    }
}
```