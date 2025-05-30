package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Validated
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

    @PostMapping(value = "/trade-executions", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeExecutionSaveResponse saveTradeExecution(@RequestBody @NotBlank(message = "FpML XML must not be blank") String fpmlXml) {
        logger.info("Received trade execution XML message");
        // TODO: Add real FpML schema validation and parsing logic
        String tradeExecutionId = UUID.randomUUID().toString();
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

    @GetMapping(value = "/trade-executions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeExecutionEntity getTradeExecution(@PathVariable("id") @NotBlank(message = "ID must not be blank") String id) {
        logger.info("Fetching trade execution with id {}", id);
        TradeExecutionEntity entity = tradeExecutionStore.get(id);
        if (entity == null) {
            logger.error("Trade execution not found: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Trade execution not found");
        }
        return entity;
    }

    @PostMapping(value = "/analytics/positions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyticsResult runAnalytics(@RequestBody @Valid AnalyticsRequest analyticsRequest) {
        logger.info("Running analytics with filters: counterparty={} instrumentType={} metrics={}",
                analyticsRequest.getFilterCounterparty(), analyticsRequest.getFilterInstrumentType(), analyticsRequest.getMetrics());
        List<TradePosition> filteredPositions = new ArrayList<>();
        for (TradeExecutionEntity entity : tradeExecutionStore.values()) {
            for (TradePosition pos : entity.getParsedPositions()) {
                boolean matches = true;
                if (analyticsRequest.getFilterCounterparty() != null && !analyticsRequest.getFilterCounterparty().equals(pos.getCounterparty())) {
                    matches = false;
                }
                if (analyticsRequest.getFilterInstrumentType() != null && !analyticsRequest.getFilterInstrumentType().equals(pos.getInstrument())) {
                    matches = false;
                }
                if (matches) filteredPositions.add(pos);
            }
        }
        AnalyticsResult result = new AnalyticsResult();
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
        lastAnalyticsResult.put("last", result);
        CompletableFuture.runAsync(() -> logger.info("Async analytics processing completed")); // TODO: Replace with real async logic
        return result;
    }

    @GetMapping(value = "/analytics/positions", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyticsResult getLastAnalytics() {
        logger.info("Retrieving last analytics result");
        AnalyticsResult result = lastAnalyticsResult.get("last");
        if (result == null) {
            logger.warn("No analytics results cached yet");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No analytics results available");
        }
        return result;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        error.put("timestamp", Instant.now().toString());
        return error;
    }

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
        @Size(min = 1, message = "Filter counterparty must not be empty")
        private String filterCounterparty;
        @Size(min = 1, message = "Filter instrument type must not be empty")
        private String filterInstrumentType;
        @NotEmpty(message = "Metrics list must not be empty")
        private List<@NotBlank(message = "Metric must not be blank") String> metrics;
    }

    @Data
    @NoArgsConstructor
    static class AnalyticsResult {
        private Long aggregateNotional;
        private Integer positionCount;
    }
}