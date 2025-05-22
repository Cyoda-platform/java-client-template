package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-inventory")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "inventoryReport";

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, InventoryReport> reportStore = new ConcurrentHashMap<>();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

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
                .exceptionally(ex -> {
                    logger.error("Async error: {}", ex.getMessage(), ex);
                    return null;
                });
        return ResponseEntity.ok(report);
    }

    @GetMapping("/report/{reportId}")
    public ResponseEntity<InventoryReport> getReport(@PathVariable @NotBlank String reportId) {
        logger.info("Received request to get report with ID: {}", reportId);
        InventoryReport report = reportStore.get(reportId);
        if (report == null) {
            logger.error("Report not found for ID: {}", reportId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Report not found");
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
            if (rawResponse == null)
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "External API returned null");
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
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report");
        }
    }

    private String buildExternalApiUrl(Map<String, String> filters) {
        StringBuilder url = new StringBuilder("https://cgiannaros-test-v1.p.rapidapi.com/searchInventory?");
        filters.forEach((k, v) -> {
            if (!v.isEmpty()) url.append(k).append("=").append(v).append("&");
        });
        if (url.charAt(url.length() - 1) == '&' || url.charAt(url.length() - 1) == '?')
            url.deleteCharAt(url.length() - 1);
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
        Map<String, Object> additionalStats = new HashMap<>();
        additionalStats.put("uniqueCategories", items.stream().map(InventoryItem::getCategory).filter(Objects::nonNull).distinct().count());
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
        return (double) Math.round(value * factor) / factor;
    }

    @PostMapping("/entity")
    public CompletableFuture<UUID> addEntity(@RequestBody @Valid InventoryItem data) {
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, data);
    }

    @PostMapping("/entities")
    public CompletableFuture<List<UUID>> addEntities(@RequestBody @Valid List<InventoryItem> data) {
        return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, data);
    }

    @GetMapping("/entity/{id}")
    public CompletableFuture<InventoryItem> getEntity(@PathVariable UUID id) {
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(objectNode -> {
                    try {
                        // Map ObjectNode to InventoryItem ignoring technicalId
                        InventoryItem item = objectMapper.treeToValue(objectNode, InventoryItem.class);
                        // technicalId is stored in ObjectNode, but InventoryItem has id field instead
                        if (objectNode.has("technicalId")) {
                            item.setId(objectNode.get("technicalId").asText());
                        }
                        return item;
                    } catch (Exception e) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse entity");
                    }
                });
    }

    @GetMapping("/entities")
    public CompletableFuture<List<InventoryItem>> getEntities() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<InventoryItem> items = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        try {
                            InventoryItem item = objectMapper.treeToValue(node, InventoryItem.class);
                            if (node.has("technicalId")) {
                                item.setId(node.get("technicalId").asText());
                            }
                            items.add(item);
                        } catch (Exception e) {
                            logger.error("Failed to parse entity: {}", e.getMessage());
                        }
                    }
                    return items;
                });
    }

    @GetMapping("/entities/filter")
    public CompletableFuture<List<InventoryItem>> getEntitiesByCondition(@RequestParam String condition) {
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    List<InventoryItem> items = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        try {
                            InventoryItem item = objectMapper.treeToValue(node, InventoryItem.class);
                            if (node.has("technicalId")) {
                                item.setId(node.get("technicalId").asText());
                            }
                            items.add(item);
                        } catch (Exception e) {
                            logger.error("Failed to parse entity: {}", e.getMessage());
                        }
                    }
                    return items;
                });
    }

    @PutMapping("/entity/{id}")
    public CompletableFuture<UUID> updateEntity(@PathVariable UUID id, @RequestBody @Valid InventoryItem data) {
        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, data);
    }

    @DeleteMapping("/entity/{id}")
    public CompletableFuture<UUID> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
    }

    @Data
    public static class InventoryReportRequest {
        @NotNull(message = "filters cannot be null")
        @Size(min = 0, message = "filters size must be >= 0")
        private Map<@NotBlank(message = "filter key must not be blank") String,
                @NotBlank(message = "filter value must not be blank") String> filters;
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
        @JsonIgnore
        private String technicalId;
        private String id;
        private String name;
        private String category;
        private double price;
        private int quantity;
    }
}