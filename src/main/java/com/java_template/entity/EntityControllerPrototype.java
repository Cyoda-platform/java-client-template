package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/reports")
@Slf4j
public class EntityControllerPrototype {

    private final Map<String, Report> reports = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // TODO: Replace with actual external API endpoint URL if different
    private static final String EXTERNAL_API_BASE = "https://cgiannaros.github.io/Test/1.0.0/developers/searchInventory";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenerateReportResponse> generateReport(@RequestBody GenerateReportRequest request) {
        log.info("Received report generation request with filters: {}", request);

        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        reports.put(reportId, new Report(reportId, requestedAt, ReportStatus.IN_PROGRESS, null, null));
        log.info("Report {} status set to IN_PROGRESS", reportId);

        // Fire-and-forget processing asynchronously
        CompletableFuture.runAsync(() -> processReport(reportId, request)); // TODO: consider @Async if preferred

        return ResponseEntity.ok(new GenerateReportResponse(reportId, ReportStatus.IN_PROGRESS.name(), null));
    }

    @GetMapping(value = "/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Report> getReport(@PathVariable String reportId) {
        log.info("Retrieving report with id {}", reportId);
        Report report = reports.get(reportId);
        if (report == null) {
            log.error("Report with id {} not found", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseEntity.ok(report);
    }

    private void processReport(String reportId, GenerateReportRequest request) {
        try {
            log.info("Processing report generation for reportId {}", reportId);

            URI externalUri = buildExternalUri(request);
            log.info("Calling external API: {}", externalUri);

            String externalResponse = restTemplate.getForObject(externalUri, String.class);
            if (!StringUtils.hasText(externalResponse)) {
                throw new IllegalStateException("Empty response from external API");
            }

            JsonNode rootNode = objectMapper.readTree(externalResponse);
            JsonNode itemsNode = rootNode.isArray() ? rootNode : rootNode.path("items");
            if (itemsNode == null || !itemsNode.isArray()) {
                throw new IllegalStateException("Unexpected external API response format: 'items' array missing");
            }

            List<InventoryItem> items = new ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                InventoryItem item = parseInventoryItem(itemNode);
                if (item != null) {
                    items.add(item);
                }
            }

            log.info("Fetched {} inventory items from external API", items.size());

            ReportMetrics metrics = calculateMetrics(items);

            Report report = new Report(reportId, Instant.now(), ReportStatus.COMPLETED, metrics, items);
            reports.put(reportId, report);

            log.info("Report {} processing COMPLETED", reportId);

        } catch (Exception e) {
            log.error("Error processing report {}: {}", reportId, e.getMessage(), e);
            reports.put(reportId, new Report(reportId, Instant.now(), ReportStatus.FAILED, null, null, e.getMessage()));
        }
    }

    private URI buildExternalUri(GenerateReportRequest request) {
        StringBuilder sb = new StringBuilder(EXTERNAL_API_BASE);
        List<String> params = new ArrayList<>();

        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            params.add("category=" + request.getCategory());
        }
        if (request.getMinPrice() != null) {
            params.add("minPrice=" + request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            params.add("maxPrice=" + request.getMaxPrice());
        }
        if (request.getDateFrom() != null && !request.getDateFrom().isBlank()) {
            params.add("dateFrom=" + request.getDateFrom());
        }
        if (request.getDateTo() != null && !request.getDateTo().isBlank()) {
            params.add("dateTo=" + request.getDateTo());
        }

        if (!params.isEmpty()) {
            sb.append("?").append(String.join("&", params));
        }

        return URI.create(sb.toString());
    }

    private InventoryItem parseInventoryItem(JsonNode node) {
        try {
            String itemId = node.path("itemId").asText(null);
            String name = node.path("name").asText(null);
            String category = node.path("category").asText(null);
            Double price = node.path("price").isNumber() ? node.path("price").asDouble() : null;
            Integer quantity = node.path("quantity").isInt() ? node.path("quantity").asInt() : null;

            if (itemId == null || name == null || price == null || quantity == null) {
                log.warn("Skipping inventory item with missing required fields: {}", node);
                return null;
            }

            return new InventoryItem(itemId, name, category, price, quantity);
        } catch (Exception e) {
            log.warn("Failed to parse inventory item: {}", e.getMessage());
            return null;
        }
    }

    private ReportMetrics calculateMetrics(List<InventoryItem> items) {
        int totalItems = items.size();
        double totalValue = 0d;
        double sumPrices = 0d;
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;

        for (InventoryItem item : items) {
            double price = item.getPrice();
            int quantity = item.getQuantity();

            sumPrices += price;
            totalValue += price * quantity;

            if (price < minPrice) minPrice = price;
            if (price > maxPrice) maxPrice = price;
        }

        double averagePrice = totalItems > 0 ? sumPrices / totalItems : 0d;
        if (minPrice == Double.MAX_VALUE) minPrice = 0d;
        if (maxPrice == Double.MIN_VALUE) maxPrice = 0d;

        return new ReportMetrics(totalItems, averagePrice, totalValue, minPrice, maxPrice);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @Data
    public static class GenerateReportRequest {
        private String category;
        private Double minPrice;
        private Double maxPrice;
        private String dateFrom; // ISO8601 string
        private String dateTo;   // ISO8601 string
    }

    @Data
    @AllArgsConstructor
    public static class GenerateReportResponse {
        private String reportId;
        private String status;
        private String message; // optional
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Report {
        private String reportId;
        private Instant generatedAt;
        private ReportStatus status;
        private ReportMetrics metrics;
        private List<InventoryItem> data;
        private String errorMessage;

        public Report(String reportId, Instant generatedAt, ReportStatus status, ReportMetrics metrics, List<InventoryItem> data) {
            this.reportId = reportId;
            this.generatedAt = generatedAt;
            this.status = status;
            this.metrics = metrics;
            this.data = data;
        }
    }

    @Data
    @AllArgsConstructor
    public static class ReportMetrics {
        private int totalItems;
        private double averagePrice;
        private double totalValue;
        private double minPrice;
        private double maxPrice;
    }

    @Data
    @AllArgsConstructor
    public static class InventoryItem {
        private String itemId;
        private String name;
        private String category;
        private double price;
        private int quantity;
    }

    public enum ReportStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}