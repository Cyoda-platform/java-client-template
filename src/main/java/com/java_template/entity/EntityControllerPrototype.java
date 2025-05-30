```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<String, TradeExecutionEntity> tradeExecutionStore = new ConcurrentHashMap<>();
    private final Map<String, AnalyticsResult> lastAnalyticsResult = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    // === POST /trade-executions ===
    @PostMapping(value = "/trade-executions", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeExecutionSaveResponse saveTradeExecution(@RequestBody String fpmlXml) {
        logger.info("Received trade execution XML message");
        // TODO: Add real FpML schema validation and parsing logic
        if (fpmlXml == null || fpmlXml.trim().isEmpty()) {
            logger.error("Empty FpML XML received");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FpML XML content is empty");
        }

        // Mock parsing: generate random tradeExecutionId and a single position
        String tradeExecutionId = UUID.randomUUID().toString();

        // TODO: Replace with real FpML XML parsing to extract positions
        TradePosition position = new TradePosition(
                UUID.randomUUID().toString(),
                "InterestRateSwap",
                10000000L,
                "CounterpartyA"
        );

        TradeExecutionEntity entity = new TradeExecutionEntity(tradeExecutionId, fpmlXml, Collections.singletonList(position));
        tradeExecutionStore.put(tradeExecutionId, entity);

        logger.info("Saved trade execution with id {}", tradeExecutionId);

        return new TradeExecutionSaveResponse(tradeExecutionId, "Trade execution saved successfully.");
    }

    // === GET /trade-executions/{id} ===
    @GetMapping(value = "/trade-executions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeExecutionEntity getTradeExecution(@PathVariable("id") String id) {
        logger.info("Fetching trade execution with id {}", id);
        TradeExecutionEntity entity = tradeExecutionStore.get(id);
        if (entity == null) {
            logger.error("Trade execution not found: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade execution not found");
        }
        return entity;
    }

    // === POST /analytics/positions ===
    @PostMapping(value = "/analytics/positions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyticsResult runAnalytics(@RequestBody AnalyticsRequest analyticsRequest) {
        logger.info("Running analytics with filters: {} and metrics: {}", analyticsRequest.getFilter(), analyticsRequest.getMetrics());

        // TODO: Replace with real query/filtering logic on stored positions
        List<TradePosition> filteredPositions = new ArrayList<>();
        for (TradeExecutionEntity entity : tradeExecutionStore.values()) {
            for (TradePosition pos : entity.getParsedPositions()) {
                boolean matches = true;
                if (analyticsRequest.getFilter() != null) {
                    Filter filter = analyticsRequest.getFilter();
                    if (filter.getCounterparty() != null && !filter.getCounterparty().equals(pos.getCounterparty())) {
                        matches = false;
                    }
                    if (filter.getInstrumentType() != null && !filter.getInstrumentType().equals(pos.getInstrument())) {
                        matches = false;
                    }
                }
                if (matches) filteredPositions.add(pos);
            }
        }

        AnalyticsResult result = new AnalyticsResult();
        if (analyticsRequest.getMetrics() != null) {
            for (String metric : analyticsRequest.getMetrics()) {
                switch (metric) {
                    case "aggregateNotional":
                        long sum = filteredPositions.stream().mapToLong(TradePosition::getNotional).sum();
                        result.setAggregateNotional(sum);
                        break;
                    case "positionCount":
                        result.setPositionCount(filteredPositions.size());
                        break;
                    default:
                        logger.warn("Unknown metric requested: {}", metric);
                }
            }
        }

        // Cache last results with a fixed key (only one cached analytics in this prototype)
        lastAnalyticsResult.put("last", result);

        // TODO: Fire-and-forget analytics background processing if needed
        CompletableFuture.runAsync(() -> {
            // Placeholder for async processing if required
            logger.info("Async analytics processing completed");
        });

        return result;
    }

    // === GET /analytics/positions ===
    @GetMapping(value = "/analytics/positions", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyticsResult getLastAnalytics() {
        logger.info("Retrieving last analytics result");
        AnalyticsResult result = lastAnalyticsResult.get("last");
        if (result == null) {
            logger.warn("No analytics results cached yet");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No analytics results available");
        }
        return result;
    }

    // === Basic error handling ===
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        error.put("timestamp", Instant.now().toString());
        return error;
    }


    // ==== DTOs and Entities ====

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class TradeExecutionSaveResponse {
        private String tradeExecutionId;
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class TradeExecutionEntity {
        private String tradeExecutionId;
        private String rawFpmlXml;
        private List<TradePosition> parsedPositions;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class TradePosition {
        private String positionId;
        private String instrument;
        private long notional;
        private String counterparty;
    }

    @Data
    @NoArgsConstructor
    static class AnalyticsRequest {
        private Filter filter;
        private List<String> metrics;
    }

    @Data
    @NoArgsConstructor
    static class Filter {
        private String counterparty;
        private String instrumentType;
    }

    @Data
    @NoArgsConstructor
    static class AnalyticsResult {
        private Long aggregateNotional;
        private Integer positionCount;
    }
}
```
