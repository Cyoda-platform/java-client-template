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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_API_BASE = "https://cgiannaros-test-v1.p.rapidapi.com"; // TODO: replace with actual

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, InventoryReport> reportStore = new ConcurrentHashMap<>();

    @PostMapping("/report")
    public ResponseEntity<InventoryReport> generateReport(@RequestBody @Valid InventoryReportRequest request) {
        logger.info("Received report generation request with filters: {}", request.getFilters());
        String jobId = UUID.randomUUID().toString();
        InventoryReport report = processReportSync(request);
        report.setReportId(jobId);
        reportStore.put(jobId, report);
        logger.info("Generated report with jobId {}", jobId);
        // Async placeholder
        CompletableFuture.runAsync(() -> processReport(jobId, request))
                         .exceptionally(ex -> { logger.error("Async error: {}", ex.getMessage(), ex); return null; });
        return ResponseEntity.ok(report);
    }

    @GetMapping("/report/{reportId}")
    public ResponseEntity<InventoryReport> getReport(@PathVariable @NotBlank String reportId) {
        logger.info("Received request to get report with ID: {}", reportId);
        InventoryReport report = reportStore.get(reportId);
        if (report == null) {
            logger.error("Report not found for ID: {}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseEntity.ok(report);
    }

    @Async
    private void processReport(String jobId, InventoryReportRequest request) {
        logger.info("Async processing started for jobId {}", jobId);
        InventoryReport report = processReportSync(request);
        reportStore.put(jobId, report);
        logger.info("Async processing finished for jobId {}", jobId);
    }

    private InventoryReport processReportSync(InventoryReportRequest request) {
        try {
            String externalUrl = buildExternalApiUrl(request.getFilters());
            logger.info("Calling external API URL: {}", externalUrl);
            String rawResponse = restTemplate.getForObject(new URI(externalUrl), String.class);
            if (rawResponse == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External API returned null");
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            JsonNode itemsNode = rootNode.has("items") ? rootNode.get("items")
                                : rootNode.isArray() ? rootNode
                                : objectMapper.createArrayNode();
            List<InventoryItem> items = new ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                InventoryItem item = parseInventoryItem(itemNode);
                if (item != null) items.add(item);
            }
            return calculateReport(items);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error processing report: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report");
        }
    }

    private String buildExternalApiUrl(Map<String,String> filters) {
        StringBuilder url = new StringBuilder(EXTERNAL_API_BASE + "/searchInventory?");
        filters.forEach((k,v) -> { if (!v.isEmpty()) url.append(k).append("=").append(v).append("&"); });
        if (url.charAt(url.length()-1)=='&' || url.charAt(url.length()-1)=='?') url.deleteCharAt(url.length()-1);
        return url.toString();
    }

    private InventoryItem parseInventoryItem(JsonNode itemNode) {
        try {
            String id = itemNode.has("id") ? itemNode.get("id").asText() : null;
            String name = itemNode.has("name") ? itemNode.get("name").asText() : null;
            String category = itemNode.has("category") ? itemNode.get("category").asText() : null;
            double price = itemNode.has("price") ? itemNode.get("price").asDouble(0.0) : 0.0;
            int quantity = itemNode.has("quantity") ? itemNode.get("quantity").asInt(0) : 0;
            return new InventoryItem(id, name, category, price, quantity);
        } catch (Exception e) {
            logger.error("Failed to parse inventory item: {}", e.getMessage());
            return null;
        }
    }

    private InventoryReport calculateReport(List<InventoryItem> items) {
        int totalItems = items.stream().mapToInt(InventoryItem::getQuantity).sum();
        double totalValue = items.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
        double averagePrice = totalItems > 0 ? totalValue / totalItems : 0.0;
        Map<String,Object> additionalStats = new HashMap<>();
        additionalStats.put("uniqueCategories", items.stream().map(InventoryItem::getCategory).filter(Objects::nonNull).distinct().count());
        InventoryReport report = new InventoryReport();
        report.setTotalItems(totalItems);
        report.setAveragePrice(round(averagePrice,2));
        report.setTotalValue(round(totalValue,2));
        report.setAdditionalStats(additionalStats);
        report.setItems(items);
        return report;
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long)Math.pow(10, places);
        return (double)Math.round(value * factor) / factor;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        Map<String,String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", String.valueOf(ex.getStatusCode().value()));
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String,String> error = new HashMap<>();
        error.put("error","Internal server error");
        error.put("status",String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Data
    public static class InventoryReportRequest {
        @NotNull(message="filters cannot be null")
        @Size(min=0,message="filters size must be >= 0")
        private Map<@NotBlank(message="filter key must not be blank") String,
                    @NotBlank(message="filter value must not be blank") String> filters;
    }

    @Data
    public static class InventoryReport {
        private String reportId;
        private int totalItems;
        private double averagePrice;
        private double totalValue;
        private Map<String,Object> additionalStats;
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