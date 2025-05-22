Certainly! To move all async logic into the workflow function `processInventoryReport` as required, we will:

- Move the async report generation task out of the controller method and into the workflow function.
- The workflow function will asynchronously call external APIs, process data, and enrich/modify the entity ObjectNode before persistence.
- The controller methods become simpler, just calling `entityService.addItem` with the workflow function.
- Any async "fire and forget" or data enrichment logic must go inside the workflow function.
- Since the workflow function receives the entity as an `ObjectNode` (not a typed POJO), we will rewrite the workflow function accordingly.
- We cannot modify the current entity via `entityService` in the workflow, but we can call other entity models if needed.

---

Here's the **full updated code** with these changes applied:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-inventory")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "inventoryReport";

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Controller endpoint simplified: just add entity with workflow processing.
     */
    @PostMapping("/entity")
    public CompletableFuture<UUID> addEntity(@RequestBody @Valid InventoryItem data) {
        // Convert InventoryItem POJO to ObjectNode
        ObjectNode entityNode = objectMapper.valueToTree(data);
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entityNode, this::processInventoryReport);
    }

    /**
     * Workflow function to process InventoryReport entity asynchronously before persistence.
     * It will:
     * - Call external API with filters from entity
     * - Parse and aggregate results
     * - Enrich entity JSON with report data
     * - Return modified entity ObjectNode for persistence
     */
    private CompletableFuture<ObjectNode> processInventoryReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Workflow processInventoryReport started");

                // Extract filters from entity (expecting filters as JSON object or string)
                Map<String, String> filters = new HashMap<>();
                JsonNode filtersNode = entity.get("filters");
                if (filtersNode != null && filtersNode.isObject()) {
                    filtersNode.fieldNames().forEachRemaining(field -> {
                        String value = filtersNode.get(field).asText("");
                        if (!value.isEmpty()) filters.put(field, value);
                    });
                }

                // Call external API to get inventory items based on filters
                String externalUrl = buildExternalApiUrl(filters);
                logger.info("Calling external API URL: {}", externalUrl);

                RestTemplate restTemplate = new RestTemplate();
                String rawResponse = restTemplate.getForObject(new URI(externalUrl), String.class);
                if (rawResponse == null) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "External API returned null");
                }

                JsonNode rootNode = objectMapper.readTree(rawResponse);
                JsonNode itemsNode = rootNode.has("items") ? rootNode.get("items")
                        : rootNode.isArray() ? rootNode
                        : objectMapper.createArrayNode();

                List<InventoryItem> items = new ArrayList<>();
                for (JsonNode itemNode : itemsNode) {
                    InventoryItem item = parseInventoryItem(itemNode);
                    if (item != null) items.add(item);
                }

                // Calculate report stats
                InventoryReport report = calculateReport(items);
                // Add raw items to report JSON node
                report.setItems(items);

                // Convert report POJO to ObjectNode
                ObjectNode reportNode = objectMapper.valueToTree(report);

                // Merge / replace fields in the original entity node
                // Remove 'filters' from entity if you want to keep the report clean
                entity.remove("filters");

                // Add all report fields into entity node
                reportNode.fieldNames().forEachRemaining(field -> {
                    entity.set(field, reportNode.get(field));
                });

                logger.info("Workflow processInventoryReport finished");

                return entity;

            } catch (Exception e) {
                logger.error("Error in workflow processInventoryReport: {}", e.getMessage(), e);
                // In case of error, rethrow as RuntimeException to fail the workflow
                throw new RuntimeException("Workflow processing failed", e);
            }
        });
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
            logger.warn("Failed to parse inventory item: {}", e.getMessage());
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
        return report;
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        return (double) Math.round(value * factor) / factor;
    }

    // --- Other endpoints can be similarly simplified or omitted for brevity ---

    @Data
    public static class InventoryReport {
        private int totalItems;
        private double averagePrice;
        private double totalValue;
        private Map<String, Object> additionalStats;
        private List<InventoryItem> items;
    }

    @Data
    @NoArgsConstructor
    public static class InventoryItem {
        private String id;
        private String name;
        private String category;
        private double price;
        private int quantity;

        public InventoryItem(String id, String name, String category, double price, int quantity) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.quantity = quantity;
        }
    }

    @Data
    public static class InventoryReportRequest {
        @NotNull(message = "filters cannot be null")
        @Size(min = 0, message = "filters size must be >= 0")
        private Map<@NotBlank(message = "filter key must not be blank") String,
                @NotBlank(message = "filter value must not be blank") String> filters;
    }
}
```

---

### Summary of changes:

- **Moved all async logic (external API call, data parsing, report calculations) into `processInventoryReport` workflow function.**
- This function receives the entity as an `ObjectNode` and modifies it directly (adding/removing fields).
- The controller method `addEntity` is greatly simplified — it just converts the POJO to `ObjectNode` and calls `entityService.addItem` with the workflow function.
- Removed all async report generation from controller endpoints.
- The workflow function fully replaces async and enrichment tasks before persistence.
- The entity stored in the database after `addItem` will have all computed report fields.
- The workflow function returns a `CompletableFuture<ObjectNode>` as required, asynchronously applying all logic.
- The existing validation annotations and POJOs remain unchanged.

---

This approach fully complies with the requirement to "free" controllers from async logic and embed it in the workflow function that is executed right before persistence.

If you want, I can also help simplify or refactor other endpoints similarly or update batch add logic. Just ask!