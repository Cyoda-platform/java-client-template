package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function for TradeExecutionEntity.
     * Parses rawFpmlXml, builds parsedPositions array, enriches entity before persistence.
     * No add/update/delete for this entityModel allowed to avoid recursion.
     *
     * @param entityNode ObjectNode representing the entity to be persisted
     * @return CompletableFuture with modified ObjectNode after async processing
     */
    private CompletableFuture<ObjectNode> processTradeExecutionEntity(ObjectNode entityNode) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Workflow processTradeExecutionEntity started");

            // Extract rawFpmlXml (required)
            JsonNode rawFpmlXmlNode = entityNode.get("rawFpmlXml");
            if (rawFpmlXmlNode == null || rawFpmlXmlNode.isNull() || rawFpmlXmlNode.asText().isEmpty()) {
                throw new IllegalArgumentException("rawFpmlXml is missing or empty");
            }
            String rawFpmlXml = rawFpmlXmlNode.asText();

            // Simulated parsing of the rawFpmlXml to create parsedPositions array
            ArrayNode positionsArray = objectMapper.createArrayNode();

            // Example: one dummy position, replace with real parsing logic
            ObjectNode position1 = objectMapper.createObjectNode();
            position1.put("positionId", UUID.randomUUID().toString());
            position1.put("instrument", "InterestRateSwap");
            position1.put("notional", 10_000_000L);
            position1.put("counterparty", "CounterpartyA");
            positionsArray.add(position1);

            // Add parsedPositions to entityNode
            entityNode.set("parsedPositions", positionsArray);

            // Additional enrichment or validation can be added here

            log.info("Workflow processTradeExecutionEntity completed with {} parsed positions", positionsArray.size());
            return entityNode;
        });
    }

    /**
     * Workflow function for AnalyticsRequest entity.
     * This can be used if you want to preprocess or validate analytics request entities before persistence.
     * Currently no persistence for analytics request, but placeholder for demonstration.
     */
    private CompletableFuture<ObjectNode> processAnalyticsRequest(ObjectNode entityNode) {
        return CompletableFuture.completedFuture(entityNode);
    }

    @PostMapping(value = "/trade-executions", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<TradeExecutionSaveResponse> saveTradeExecution(@RequestBody @NotBlank(message = "FpML XML must not be blank") String fpmlXml) {
        log.info("Received trade execution XML message");

        // Create minimal entity with rawFpmlXml and tradeExecutionId
        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("tradeExecutionId", UUID.randomUUID().toString());
        entityNode.put("rawFpmlXml", fpmlXml);

        // Call entityService.addItem with workflow function
        return entityService.addItem(
                "TradeExecutionEntity",
                ENTITY_VERSION,
                entityNode,
                this::processTradeExecutionEntity
        ).thenApply(technicalId -> {
            log.info("Saved trade execution with technicalId {}", technicalId);
            return new TradeExecutionSaveResponse(technicalId.toString(), "Trade execution saved successfully.");
        });
    }

    @GetMapping(value = "/trade-executions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ObjectNode> getTradeExecution(@PathVariable("id") @NotBlank String id) {
        log.info("Fetching trade execution with id {}", id);

        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid ID format");
        }

        return entityService.getItem("TradeExecutionEntity", ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty()) {
                        log.error("Trade execution not found: {}", id);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Trade execution not found");
                    }
                    return item;
                });
    }

    @PostMapping(value = "/analytics/positions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<AnalyticsResult> runAnalytics(@Valid @RequestBody AnalyticsRequest analyticsRequest) {
        log.info("Running analytics with filters: counterparty={} instrumentType={} metrics={}",
                analyticsRequest.getFilterCounterparty(), analyticsRequest.getFilterInstrumentType(), analyticsRequest.getMetrics());

        // Create ObjectNode from AnalyticsRequest for workflow if needed
        ObjectNode analyticsNode = objectMapper.valueToTree(analyticsRequest);

        // If needed, use workflow function to preprocess analytics request (currently noop)
        return processAnalyticsRequest(analyticsNode).thenCompose(processedNode ->

            // Retrieve all TradeExecutionEntity items asynchronously
            entityService.getItems("TradeExecutionEntity", ENTITY_VERSION).thenApply(items -> {
                List<TradePosition> filteredPositions = new ArrayList<>();

                for (JsonNode node : items) {
                    JsonNode parsedPositionsNode = node.get("parsedPositions");
                    if (parsedPositionsNode != null && parsedPositionsNode.isArray()) {
                        for (JsonNode posNode : parsedPositionsNode) {
                            TradePosition pos;
                            try {
                                pos = objectMapper.treeToValue(posNode, TradePosition.class);
                            } catch (Exception e) {
                                log.warn("Failed to parse TradePosition: {}", e.getMessage());
                                continue;
                            }

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
                            log.warn("Unknown metric requested: {}", metric);
                    }
                }

                log.info("Analytics completed with {} positions matched", filteredPositions.size());
                return result;
            })
        );
    }

    @GetMapping(value = "/analytics/positions", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<AnalyticsResult> getLastAnalytics() {
        log.info("Retrieving last analytics result");
        // No stored analytics results, return completed exceptionally with 404
        CompletableFuture<AnalyticsResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No analytics results available")
        );
        return failedFuture;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        error.put("timestamp", Instant.now().toString());
        return error;
    }

    // DTOs and data classes

    public static class TradeExecutionSaveResponse {
        private final String tradeExecutionId;
        private final String message;

        public TradeExecutionSaveResponse(String tradeExecutionId, String message) {
            this.tradeExecutionId = tradeExecutionId;
            this.message = message;
        }

        public String getTradeExecutionId() {
            return tradeExecutionId;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class TradePosition {
        private String positionId;
        private String instrument;
        private long notional;
        private String counterparty;

        public TradePosition() {
        }

        public String getPositionId() {
            return positionId;
        }

        public void setPositionId(String positionId) {
            this.positionId = positionId;
        }

        public String getInstrument() {
            return instrument;
        }

        public void setInstrument(String instrument) {
            this.instrument = instrument;
        }

        public long getNotional() {
            return notional;
        }

        public void setNotional(long notional) {
            this.notional = notional;
        }

        public String getCounterparty() {
            return counterparty;
        }

        public void setCounterparty(String counterparty) {
            this.counterparty = counterparty;
        }
    }

    public static class AnalyticsRequest {

        private String filterCounterparty;

        private String filterInstrumentType;

        @NotEmpty(message = "Metrics list must not be empty")
        private List<@NotBlank(message = "Metric must not be blank") String> metrics;

        public AnalyticsRequest() {
        }

        public String getFilterCounterparty() {
            return filterCounterparty;
        }

        public void setFilterCounterparty(String filterCounterparty) {
            this.filterCounterparty = filterCounterparty;
        }

        public String getFilterInstrumentType() {
            return filterInstrumentType;
        }

        public void setFilterInstrumentType(String filterInstrumentType) {
            this.filterInstrumentType = filterInstrumentType;
        }

        public List<String> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<String> metrics) {
            this.metrics = metrics;
        }
    }

    public static class AnalyticsResult {
        private Long aggregateNotional;
        private Integer positionCount;

        public AnalyticsResult() {
        }

        public Long getAggregateNotional() {
            return aggregateNotional;
        }

        public void setAggregateNotional(Long aggregateNotional) {
            this.aggregateNotional = aggregateNotional;
        }

        public Integer getPositionCount() {
            return positionCount;
        }

        public void setPositionCount(Integer positionCount) {
            this.positionCount = positionCount;
        }
    }
}