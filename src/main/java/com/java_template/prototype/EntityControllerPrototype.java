```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/api/reports/inventory")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Simulated in-memory storage of reports keyed by reportId
    private final Map<String, InventoryReport> reports = new ConcurrentHashMap<>();

    // External API base URL from SwaggerHub (assuming actual endpoint for inventory search)
    private static final String EXTERNAL_API_BASE = "https://cgiannaros.github.io/Test-API/inventory";

    /**
     * POST /prototype/api/reports/inventory
     * Trigger generation of inventory report with optional filters.
     */
    @PostMapping
    public ResponseEntity<ReportGenerationResponse> generateReport(@Valid @RequestBody ReportRequest request) {
        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        logger.info("Received report generation request with filters: {}. Assigned reportId={}", request.getFilters(), reportId);
        // Put initial status as processing
        InventoryReport stubReport = new InventoryReport(reportId, requestedAt, "processing", null, null);
        reports.put(reportId, stubReport);

        // Fire-and-forget async processing of report
        CompletableFuture.runAsync(() -> processReport(reportId, request))
                .exceptionally(ex -> {
                    logger.error("Error processing reportId {}: {}", reportId, ex.getMessage(), ex);
                    InventoryReport failedReport = reports.get(reportId);
                    if (failedReport != null) {
                        failedReport.setStatus("failed");
                    }
                    return null;
                });

        ReportGenerationResponse response = new ReportGenerationResponse(reportId, "processing");
        return ResponseEntity.accepted().body(response);
    }

    /**
     * GET /prototype/api/reports/inventory/{reportId}
     * Retrieve previously generated report by ID.
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<InventoryReport> getReport(@PathVariable String reportId) {
        logger.info("Received request to get report with id={}", reportId);
        InventoryReport report = reports.get(reportId);
        if (report == null) {
            logger.error("Report with id={} not found", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        // If still processing, return 202 Accepted with current status
        if ("processing".equalsIgnoreCase(report.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(report);
        }
        return ResponseEntity.ok(report);
    }

    /**
     * Background report processing logic.
     * Retrieves external inventory data, applies filters, calculates metrics,
     * and stores completed report.
     */
    private void processReport(String reportId, ReportRequest request) {
        logger.info("Start processing reportId={}", reportId);

        // Build external API URL with query parameters based on filters
        String url = buildExternalApiUrl(request.getFilters());
        logger.info("Fetching inventory data from external API: {}", url);

        JsonNode inventoryData;
        try {
            URI uri = new URI(url);
            String rawResponse = restTemplate.getForObject(uri, String.class);
            if (rawResponse == null) {
                throw new IllegalStateException("Empty response from external API");
            }
            inventoryData = objectMapper.readTree(rawResponse);
        } catch (Exception e) {
            logger.error("Failed to fetch or parse external inventory data for reportId={}: {}", reportId, e.getMessage(), e);
            InventoryReport report = reports.get(reportId);
            if (report != null) {
                report.setStatus("failed");
            }
            return;
        }

        // Parse inventory items from JSON (assuming JSON array or object with array inside)
        List<InventoryItem> items = parseInventoryItems(inventoryData);

        // Apply additional filtering on items if needed (if external API filtering is incomplete)
        // TODO: Implement additional filtering here if required

        // Calculate metrics
        InventoryMetrics metrics = calculateMetrics(items);

        // Store completed report
        InventoryReport completedReport = new InventoryReport(reportId, Instant.now(), "completed", metrics, items);
        reports.put(reportId, completedReport);

        logger.info("Completed reportId={} with {} items", reportId, items.size());
    }

    /**
     * Compose the external API URL with query parameters for filtering.
     * TODO: Adjust parameters according to actual external API spec.
     */
    private String buildExternalApiUrl(ReportFilters filters) {
        // Base example URL from SwaggerHub docs endpoint (mocked here):
        // The SwaggerHub URL provided is https://app.swaggerhub.com/apis/CGIANNAROS/Test/1.0.0#/developers/searchInventory
        // But no direct external API URL given, so using a placeholder URL here.
        // TODO: Replace with actual external API endpoint URL and parameter names.

        StringBuilder sb = new StringBuilder(EXTERNAL_API_BASE);
        boolean firstParam = true;

        if (filters != null) {
            if (filters.getCategory() != null) {
                sb.append(firstParam ? "?" : "&").append("category=").append(filters.getCategory());
                firstParam = false;
            }
            if (filters.getMinPrice() != null) {
                sb.append(firstParam ? "?" : "&").append("minPrice=").append(filters.getMinPrice());
                firstParam = false;
            }
            if (filters.getMaxPrice() != null) {
                sb.append(firstParam ? "?" : "&").append("maxPrice=").append(filters.getMaxPrice());
                firstParam = false;
            }
            if (filters.getDateFrom() != null) {
                sb.append(firstParam ? "?" : "&").append("dateFrom=").append(filters.getDateFrom());
                firstParam = false;
            }
            if (filters.getDateTo() != null) {
                sb.append(firstParam ? "?" : "&").append("dateTo=").append(filters.getDateTo());
            }
        }
        return sb.toString();
    }

    /**
     * Parse inventory items from external API JSON response.
     * Handles both array and object with nested array.
     */
    private List<InventoryItem> parseInventoryItems(JsonNode rootNode) {
        List<InventoryItem> items = new ArrayList<>();
        if (rootNode.isArray()) {
            for (JsonNode itemNode : rootNode) {
                InventoryItem item = parseItemNode(itemNode);
                if (item != null) {
                    items.add(item);
                }
            }
        } else if (rootNode.isObject()) {
            // Attempt to locate array node named "items" or "inventory"
            JsonNode arrNode = null;
            if (rootNode.has("items")) {
                arrNode = rootNode.get("items");
            } else if (rootNode.has("inventory")) {
                arrNode = rootNode.get("inventory");
            }
            if (arrNode != null && arrNode.isArray()) {
                for (JsonNode itemNode : arrNode) {
                    InventoryItem item = parseItemNode(itemNode);
                    if (item != null) {
                        items.add(item);
                    }
                }
            } else {
                // If root object itself has fields of one item, parse single item
                InventoryItem item = parseItemNode(rootNode);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /**
     * Parse single inventory item from JSON node.
     * Fields: itemId, name, category, price, quantity
     * If fields missing, skip or set default.
     */
    private InventoryItem parseItemNode(JsonNode node) {
        try {
            String itemId = node.hasNonNull("itemId") ? node.get("itemId").asText() : UUID.randomUUID().toString();
            String name = node.hasNonNull("name") ? node.get("name").asText() : "Unknown";
            String category = node.hasNonNull("category") ? node.get("category").asText() : "Uncategorized";
            double price = node.hasNonNull("price") ? node.get("price").asDouble() : 0.0;
            int quantity = node.hasNonNull("quantity") ? node.get("quantity").asInt() : 0;
            return new InventoryItem(itemId, name, category, price, quantity);
        } catch (Exception e) {
            logger.warn("Failed to parse inventory item node: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculate metrics from list of inventory items.
     */
    private InventoryMetrics calculateMetrics(List<InventoryItem> items) {
        int totalItems = items.size();
        double totalValue = 0.0;
        double totalPrice = 0.0;
        int countWithPrice = 0;

        for (InventoryItem item : items) {
            double itemTotalValue = item.getPrice() * item.getQuantity();
            totalValue += itemTotalValue;
            if (item.getPrice() > 0) {
                totalPrice += item.getPrice();
                countWithPrice++;
            }
        }
        double averagePrice = countWithPrice > 0 ? totalPrice / countWithPrice : 0;

        return new InventoryMetrics(totalItems, averagePrice, totalValue);
    }

    /**
     * Basic error handler for ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // --- DTOs and data classes ---

    @Data
    public static class ReportRequest {
        @NotNull
        private ReportFilters filters;
    }

    @Data
    public static class ReportFilters {
        private String category;
        private Double minPrice;
        private Double maxPrice;
        private String dateFrom; // ISO8601 date string
        private String dateTo;   // ISO8601 date string
    }

    @Data
    public static class ReportGenerationResponse {
        private final String reportId;
        private final String status;
    }

    @Data
    public static class InventoryReport {
        private final String reportId;
        private final Instant generatedAt;
        private String status; // processing, completed, failed
        private InventoryMetrics metrics;
        private List<InventoryItem> data;
    }

    @Data
    public static class InventoryMetrics {
        private final int totalItems;
        private final double averagePrice;
        private final double totalValue;
    }

    @Data
    public static class InventoryItem {
        private final String itemId;
        private final String name;
        private final String category;
        private final double price;
        private final int quantity;
    }
}
```