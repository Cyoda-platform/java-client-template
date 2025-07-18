package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Validated
@RestController
@RequestMapping(path = "/prototype/api/reports/inventory")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final RestTemplate restTemplate;

    private static final String ENTITY_NAME = "InventoryReport";
    private static final String ENTITY_VERSION = "1";

    public Controller(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
        this.restTemplate = new RestTemplate();
    }

    @PostMapping
    public ResponseEntity<ReportGenerationResponse> generateReport(@RequestBody @Valid ReportRequest request) {
        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        logger.info("Received report generation request: {}. reportId={}", request, reportId);

        InventoryReport stub = new InventoryReport(reportId, requestedAt, "processing", null, null);

        // Save stub report via entityService
        entityService.addItem(ENTITY_NAME, ENTITY_VERSION, stub);

        CompletableFuture.runAsync(() -> processReport(reportId, request))
            .exceptionally(ex -> {
                logger.error("Error processing report {}: {}", reportId, ex.getMessage(), ex);
                // Update report status to failed
                try {
                    SearchConditionRequest cond = SearchConditionRequest.group("AND",
                            Condition.of("$.reportId", "EQUALS", reportId));
                    entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, cond)
                        .thenAccept(arr -> {
                            if (arr.size() > 0) {
                                JsonNode node = arr.get(0);
                                InventoryReport rep;
                                try {
                                    rep = objectMapper.treeToValue(node, InventoryReport.class);
                                    rep.setStatus("failed");
                                    entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, UUID.fromString(node.get("technicalId").asText()), rep);
                                } catch (Exception e) {
                                    logger.error("Failed to update report status to failed: {}", e.getMessage(), e);
                                }
                            }
                        }).join();
                } catch (Exception e) {
                    logger.error("Failed to set report status failed on exception: {}", e.getMessage(), e);
                }
                return null;
            });

        return ResponseEntity.accepted().body(new ReportGenerationResponse(reportId, "processing"));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<InventoryReport> getReport(@PathVariable String reportId) {
        logger.info("Fetching report {}", reportId);

        SearchConditionRequest cond = SearchConditionRequest.group("AND",
                Condition.of("$.reportId", "EQUALS", reportId));

        try {
            ArrayList<JsonNode> arr = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, cond).get();
            if (arr.isEmpty()) {
                logger.error("Report {} not found", reportId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
            }
            JsonNode node = arr.get(0);
            InventoryReport report = objectMapper.treeToValue(node, InventoryReport.class);

            if ("processing".equalsIgnoreCase(report.getStatus())) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(report);
            }
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    private void processReport(String reportId, ReportRequest request) {
        // Removed business logic moved to processors/criteria
        // This method should only handle minimal orchestration or can be removed if processors handle everything
        // Keeping this basic stub call for reference or can be removed if not needed
        logger.info("Processing report asynchronously for reportId {}", reportId);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getMessage());
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    // DTO and Entity classes moved or duplicated here for Controller usage

    public static class ReportRequest {
        @Size(max = 50)
        private String category;
        @PositiveOrZero
        private Double minPrice;
        @PositiveOrZero
        private Double maxPrice;
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}.*", message = "Must be ISO8601")
        private String dateFrom;
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}.*", message = "Must be ISO8601")
        private String dateTo;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Double getMinPrice() {
            return minPrice;
        }

        public void setMinPrice(Double minPrice) {
            this.minPrice = minPrice;
        }

        public Double getMaxPrice() {
            return maxPrice;
        }

        public void setMaxPrice(Double maxPrice) {
            this.maxPrice = maxPrice;
        }

        public String getDateFrom() {
            return dateFrom;
        }

        public void setDateFrom(String dateFrom) {
            this.dateFrom = dateFrom;
        }

        public String getDateTo() {
            return dateTo;
        }

        public void setDateTo(String dateTo) {
            this.dateTo = dateTo;
        }
    }

    public static class ReportGenerationResponse {
        private final String reportId;
        private final String status;

        public ReportGenerationResponse(String reportId, String status) {
            this.reportId = reportId;
            this.status = status;
        }

        public String getReportId() {
            return reportId;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class InventoryReport {
        private final String reportId;
        private final Instant generatedAt;
        private String status;
        private InventoryMetrics metrics;
        private List<InventoryItem> data;

        public InventoryReport(String reportId, Instant generatedAt, String status, InventoryMetrics metrics, List<InventoryItem> data) {
            this.reportId = reportId;
            this.generatedAt = generatedAt;
            this.status = status;
            this.metrics = metrics;
            this.data = data;
        }

        public String getReportId() {
            return reportId;
        }

        public Instant getGeneratedAt() {
            return generatedAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public InventoryMetrics getMetrics() {
            return metrics;
        }

        public void setMetrics(InventoryMetrics metrics) {
            this.metrics = metrics;
        }

        public List<InventoryItem> getData() {
            return data;
        }

        public void setData(List<InventoryItem> data) {
            this.data = data;
        }
    }

    public static class InventoryMetrics {
        private final int totalItems;
        private final double averagePrice;
        private final double totalValue;

        public InventoryMetrics(int totalItems, double averagePrice, double totalValue) {
            this.totalItems = totalItems;
            this.averagePrice = averagePrice;
            this.totalValue = totalValue;
        }

        public int getTotalItems() {
            return totalItems;
        }

        public double getAveragePrice() {
            return averagePrice;
        }

        public double getTotalValue() {
            return totalValue;
        }
    }

    public static class InventoryItem {
        private final String itemId;
        private final String name;
        private final String category;
        private final double price;
        private final int quantity;

        public InventoryItem(String itemId, String name, String category, double price, int quantity) {
            this.itemId = itemId;
            this.name = name;
            this.category = category;
            this.price = price;
            this.quantity = quantity;
        }

        public String getItemId() {
            return itemId;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public double getPrice() {
            return price;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    // Placeholder classes for Condition and SearchConditionRequest since criteria folder was missing
    // These would normally be imported from common.util or similar package

    public static class Condition {
        private final String jsonPath;
        private final String operator;
        private final String value;

        private Condition(String jsonPath, String operator, String value) {
            this.jsonPath = jsonPath;
            this.operator = operator;
            this.value = value;
        }

        public static Condition of(String jsonPath, String operator, String value) {
            return new Condition(jsonPath, operator, value);
        }

        public String getJsonPath() {
            return jsonPath;
        }

        public String getOperator() {
            return operator;
        }

        public String getValue() {
            return value;
        }
    }

    public static class SearchConditionRequest {
        private final String groupOperator;
        private final List<Condition> conditions;

        private SearchConditionRequest(String groupOperator, List<Condition> conditions) {
            this.groupOperator = groupOperator;
            this.conditions = conditions;
        }

        public static SearchConditionRequest group(String groupOperator, Condition... conditions) {
            return new SearchConditionRequest(groupOperator, Arrays.asList(conditions));
        }

        public String getGroupOperator() {
            return groupOperator;
        }

        public List<Condition> getConditions() {
            return conditions;
        }
    }
}