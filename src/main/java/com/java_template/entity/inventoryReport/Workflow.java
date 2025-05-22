package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-inventory")
@Validated
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private static final String ENTITY_NAME = "inventoryReport";

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/entity")
    public CompletableFuture<UUID> addEntity(@RequestBody @Valid InventoryItem data) {
        ObjectNode entityNode = objectMapper.valueToTree(data);
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entityNode, this::processInventoryReport);
    }

    @PostMapping("/entities")
    public CompletableFuture<List<UUID>> addEntities(@RequestBody @Valid List<InventoryItem> data) {
        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (InventoryItem item : data) {
            ObjectNode entityNode = objectMapper.valueToTree(item);
            futures.add(entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entityNode, this::processInventoryReport));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<UUID> ids = new ArrayList<>();
                    for (CompletableFuture<UUID> f : futures) {
                        ids.add(f.join());
                    }
                    return ids;
                });
    }

    @GetMapping("/entity/{id}")
    public CompletableFuture<InventoryItem> getEntity(@PathVariable UUID id) {
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(objectNode -> {
                    try {
                        InventoryItem item = objectMapper.treeToValue(objectNode, InventoryItem.class);
                        if (objectNode.has("technicalId")) {
                            item.setId(objectNode.get("technicalId").asText());
                        }
                        return item;
                    } catch (Exception e) {
                        logger.error("Failed to parse entity: {}", e.getMessage());
                        throw new RuntimeException("Failed to parse entity");
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
        ObjectNode entityNode = objectMapper.valueToTree(data);
        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, entityNode);
    }

    @DeleteMapping("/entity/{id}")
    public CompletableFuture<UUID> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
    }

    // Workflow orchestration only
    private CompletableFuture<ObjectNode> processInventoryReport(ObjectNode entity) {
        return processExtractFilters(entity)
                .thenCompose(filters -> processCallExternalApi(entity, filters))
                .thenCompose(items -> processCalculateReport(entity, items));
    }

    private CompletableFuture<Map<String, String>> processExtractFilters(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> filters = new HashMap<>();
            JsonNode filtersNode = entity.get("filters");
            if (filtersNode != null && filtersNode.isObject()) {
                filtersNode.fieldNames().forEachRemaining(field -> {
                    String value = filtersNode.get(field).asText("");
                    if (!value.isEmpty()) filters.put(field, value);
                });
            }
            return filters;
        });
    }

    private CompletableFuture<List<InventoryItem>> processCallExternalApi(ObjectNode entity, Map<String, String> filters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String externalUrl = buildExternalApiUrl(filters);
                logger.info("Calling external API URL: {}", externalUrl);
                String rawResponse = restTemplate.getForObject(new URI(externalUrl), String.class);
                if (rawResponse == null) {
                    throw new RuntimeException("External API returned null");
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
                return items;
            } catch (Exception e) {
                logger.error("Error calling external API: {}", e.getMessage(), e);
                throw new RuntimeException("External API call failed", e);
            }
        });
    }

    private CompletableFuture<ObjectNode> processCalculateReport(ObjectNode entity, List<InventoryItem> items) {
        return CompletableFuture.supplyAsync(() -> {
            InventoryReport report = calculateReport(items);
            report.setItems(items);
            ObjectNode reportNode = objectMapper.valueToTree(report);
            entity.remove("filters");
            reportNode.fieldNames().forEachRemaining(field -> entity.set(field, reportNode.get(field)));
            return entity;
        });
    }

    private String buildExternalApiUrl(Map<String, String> filters) {
        StringBuilder url = new StringBuilder("https://cgiannaros-test-v1.p.rapidapi.com/searchInventory?");
        filters.forEach((k, v) -> {
            if (!v.isEmpty()) url.append(k).append("=").append(v).append("&");
        });
        if (url.charAt(url.length() - 1) == '&' || url.charAt(url.length() - 1) == '?') url.deleteCharAt(url.length() - 1);
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
        return report;
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        return (double) Math.round(value * factor) / factor;
    }
}