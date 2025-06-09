package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
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
import org.springframework.web.client.ResponseStatusException;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/new/api/flights")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/search")
    public DeferredResult<ResponseEntity<FlightSearchResult>> searchFlights(@RequestBody @Valid FlightSearchCriteria criteria) {
        String jobId = UUID.randomUUID().toString();
        DeferredResult<ResponseEntity<FlightSearchResult>> output = new DeferredResult<>();

        // Prepare the initial entity data
        ObjectNode flightSearchResultNode = objectMapper.createObjectNode();
        flightSearchResultNode.put("jobId", jobId);
        flightSearchResultNode.put("departureAirport", criteria.getDepartureAirport());
        flightSearchResultNode.put("arrivalAirport", criteria.getArrivalAirport());
        flightSearchResultNode.put("departureDate", criteria.getDepartureDate());
        flightSearchResultNode.put("returnDate", criteria.getReturnDate());
        flightSearchResultNode.put("passengers", criteria.getPassengers());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "FlightSearchResult",
                ENTITY_VERSION,
                flightSearchResultNode,
                this::processFlightSearchResult
        );

        idFuture.thenAccept(technicalId -> {
            try {
                FlightSearchResult result = objectMapper.treeToValue(flightSearchResultNode, FlightSearchResult.class);
                output.setResult(ResponseEntity.ok(result));
            } catch (Exception e) {
                logger.error("Error converting entity to FlightSearchResult", e);
                output.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing search result"));
            }
        }).exceptionally(e -> {
            logger.error("Error saving search result", e);
            output.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving search result"));
            return null;
        });

        return output;
    }

    private ObjectNode processFlightSearchResult(ObjectNode entity) {
        // Mock external API call and process logic
        try {
            // Simulate an API call or any async operation
            // In reality, you would make an HTTP request here
            logger.info("Processing FlightSearchResult entity asynchronously: {}", entity);
            // Example modification
            entity.put("status", "processed");

            // You can add supplementary data from another entity model if needed
            // For example, adding additional info from a secondary model
            // ObjectNode supplementaryData = entityService.getItem(...).get();
            // entity.set("supplementaryData", supplementaryData);
        } catch (Exception e) {
            logger.error("Error processing FlightSearchResult entity", e);
            // Handle any exceptions during processing
        }
        return entity;
    }

    @GetMapping("/results")
    public ResponseEntity<FlightSearchResult> getSearchResults(@RequestParam @NotNull String jobId) {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "FlightSearchResult",
                ENTITY_VERSION,
                UUID.fromString(jobId)
        );

        try {
            ObjectNode resultNode = itemFuture.get();
            FlightSearchResult result = objectMapper.treeToValue(resultNode, FlightSearchResult.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error retrieving search result", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving search result");
        }
    }

    @PostMapping("/sort-filter")
    public ResponseEntity<FlightSearchResult> sortAndFilterResults(@RequestBody @Valid SortFilterCriteria criteria) {
        // Mock logic for sorting and filtering
        // TODO: Implement sorting and filtering logic based on criteria
        logger.info("Sorting and filtering results");
        return ResponseEntity.ok(new FlightSearchResult()); // Mock result
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException", ex);
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getStatusCode().toString());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class FlightSearchCriteria {
        @NotBlank
        private String departureAirport;
        @NotBlank
        private String arrivalAirport;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String departureDate;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String returnDate;
        @Min(1)
        private int passengers;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class SortFilterCriteria {
        @NotBlank
        private String sortBy;
        @NotNull
        private Filter filter;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Filter {
        @Max(10000)
        private double maxPrice;
        @NotNull
        private TimeRange timeRange;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class TimeRange {
        @Pattern(regexp = "\\d{2}:\\d{2}")
        private String earliestDeparture;
        @Pattern(regexp = "\\d{2}:\\d{2}")
        private String latestArrival;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class FlightSearchResult {
        private String jobId;
        private String departureAirport;
        private String arrivalAirport;
        private String departureDate;
        private String returnDate;
        private int passengers;
        private String status;
    }
}