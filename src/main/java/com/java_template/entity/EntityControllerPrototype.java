```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_API_BASE = "https://cgiannaros-test-v1.p.rapidapi.com"; 
    // NOTE: The SwaggerHub API link is for API documentation. For a working prototype,
    // this is assumed to be proxied or the real external API endpoint here.
    // TODO: Replace EXTERNAL_API_BASE with the actual external API base URL if different.

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage of generated reports
    private final Map<String, InventoryReport> reportStore = new ConcurrentHashMap<>();

    @PostMapping(value = "/report", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryReport> generateReport(@RequestBody InventoryReportRequest request) {
        logger.info("Received report generation request with filters: {}", request.getFilters());

        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Fire-and-forget processing - to simulate async processing if desired
        CompletableFuture.runAsync(() -> processReport(jobId, request))
                .exceptionally(ex -> {
                    logger.error("Error during async report processing for jobId {}: {}", jobId, ex.getMessage(), ex);
                    return null;
                });

        // For prototype, we return the report immediately after synchronous processing
        // but to comply with the spec, we process synchronously here.
        InventoryReport report = processReportSync(request);

        // Store the report for retrieval later
        reportStore.put(jobId, report);
        logger.info("Generated report with jobId {}", jobId);

        // Attach jobId in response header (optional) or in response body - here we embed in response
        report.setReportId(jobId);

        return ResponseEntity.ok(report);
    }

    @GetMapping(value = "/report/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryReport> getReport(@PathVariable String reportId) {
        logger.info("Received request to get report with ID: {}", reportId);
        InventoryReport report = reportStore.get(reportId);
        if (report == null) {
            logger.error("Report not found for ID: {}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseEntity.ok(report);
    }

    // --- Internal processing methods ---

    /**
     * Synchronous processing of the report (used for prototype).
     */
    private InventoryReport processReportSync(InventoryReportRequest request) {
        try {
            // Build external API URL with filters
            String externalUrl = buildExternalApiUrl(request.getFilters());
            logger.info("Calling external API URL: {}", externalUrl);

            // Call external API
            String rawResponse = restTemplate.getForObject(new URI(externalUrl), String.class);
            if (rawResponse == null) {
                logger.error("External API returned null response");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External API returned null");
            }

            JsonNode rootNode = objectMapper.readTree(rawResponse);

            // TODO: Adjust JSON path based on actual external API response structure
            // Assume inventory items are in an array called "items" or root is an array
            JsonNode itemsNode;
            if (rootNode.has("items")) {
                itemsNode = rootNode.get("items");
            } else if (rootNode.isArray()) {
                itemsNode = rootNode;
            } else {
                // fallback
                itemsNode = objectMapper.createArrayNode();
            }

            List<InventoryItem> items = new ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                InventoryItem item = parseInventoryItem(itemNode);
                if (item != null) {
                    items.add(item);
                }
            }

            return calculateReport(items);

        } catch (Exception e) {
            logger.error("Error processing report synchronously: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report");
        }
    }

    /**
     * Fire-and-forget async processing placeholder.
     */
    @Async
    private void processReport(String jobId, InventoryReportRequest request) {
        // TODO: Implement any long-running processing or caching if needed
        logger.info("Async processing started for jobId {}", jobId);
        InventoryReport report = processReportSync(request);
        reportStore.put(jobId, report);
        logger.info("Async processing finished for jobId {}", jobId);
    }

    private String buildExternalApiUrl(Map<String, String> filters) {
        // TODO: Adapt this logic to the actual external API and filtering parameters required

        // For prototype, assume external API has /searchInventory endpoint accepting query params
        StringBuilder url = new StringBuilder(EXTERNAL_API_BASE + "/searchInventory?");

        if (filters != null) {
            filters.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    url.append(key).append("=").append(value).append("&");
                }
            });
        }
        // Remove trailing "&" or "?" if exists
        if (url.charAt(url.length() - 1) == '&' || url.charAt(url.length() - 1) == '?') {
            url.deleteCharAt(url.length() - 1);
        }
        return url.toString();
    }

    private InventoryItem parseInventoryItem(JsonNode itemNode) {
        try {
            // Parse fields based on expected JSON structure
            String id = itemNode.has("id") ? itemNode.get("id").asText() : null;
            String name = itemNode.has("name") ? itemNode.get("name").asText() : null;
            String category = itemNode.has("category") ? itemNode.get("category").asText() : null;
            double price = itemNode.has("price") ? itemNode.get("price").asDouble(0.0) : 0.0;
            int quantity = itemNode.has("quantity") ? itemNode.get("quantity").asInt(0) : 0;

            // TODO: Extract other fields if needed

            return new InventoryItem(id, name, category, price, quantity);
        } catch (Exception e) {
            logger.error("Failed to parse inventory item: {}", e.getMessage());
            return null;
        }
    }

    private InventoryReport calculateReport(List<InventoryItem> items) {
        int totalItems = items.stream().mapToInt(InventoryItem::getQuantity).sum();

        double totalValue = items.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();

        double averagePrice = totalItems > 0 ? totalValue / totalItems : 0.0;

        // Additional stats placeholder
        Map<String, Object> additionalStats = new HashMap<>();
        additionalStats.put("uniqueCategories", items.stream()
                .map(InventoryItem::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .count());

        InventoryReport report = new InventoryReport();
        report.setTotalItems(totalItems);
        report.setAveragePrice(round(averagePrice, 2));
        report.setTotalValue(round(totalValue, 2));
        report.setAdditionalStats(additionalStats);
        report.setItems(items);

        return report;
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        long tmp = Math.round(value * factor);
        return (double) tmp / factor;
    }

    // --- Exception Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", String.valueOf(ex.getStatusCode().value()));
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal server error");
        error.put("status", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    // --- DTOs and Data Models ---

    @Data
    public static class InventoryReportRequest {
        private Map<String, String> filters;
    }

    @Data
    public static class InventoryReport {
        private String reportId;
        private int totalItems;
        private double averagePrice;
        private double totalValue;
        private Map<String, Object> additionalStats;
        private List<InventoryItem> items;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InventoryItem {
        private String id;
        private String name;
        private String category;
        private double price;
        private int quantity;
    }
}
```
