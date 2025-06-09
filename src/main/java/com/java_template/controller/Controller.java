package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/prototype/api/inventory")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/add")
    public ResponseEntity<String> addInventoryItem(@RequestBody @Valid JsonNode data) {
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Inventory",
                ENTITY_VERSION,
                data
        );

        return ResponseEntity.status(HttpStatus.CREATED).body("Item added with ID: " + idFuture.join());
    }

    private CompletableFuture<JsonNode> processInventory(JsonNode entity) {
        ObjectNode entityObject = (ObjectNode) entity;

        // Modify the entity before persistence
        entityObject.put("processed", true);

        // Fetch supplementary data asynchronously
        return fetchSupplementaryData(entityObject).thenApply(supplementaryData -> {
            entityObject.set("supplementaryData", supplementaryData);
            logger.info("Processed entity with supplementary data: {}", entity);
            return entity;
        });
    }

    private CompletableFuture<JsonNode> fetchSupplementaryData(ObjectNode entity) {
        // Simulate fetching supplementary data asynchronously
        return CompletableFuture.supplyAsync(() -> {
            // Simulate some data fetching logic
            ObjectNode supplementaryData = objectMapper.createObjectNode();
            supplementaryData.put("info", "additional data");
            logger.info("Fetched supplementary data for entity: {}", entity);
            return supplementaryData;
        });
    }

    @PostMapping("/fetch")
    public ResponseEntity<String> fetchInventoryData(@RequestBody @Valid InventoryFilter filter) {
        String jobId = "job_" + System.currentTimeMillis();

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND",
                Condition.of("$.category", "EQUALS", filter.getCategory()),
                Condition.of("$.price", "GREATER_OR_EQUAL", filter.getMinPrice()),
                Condition.of("$.price", "LESS_OR_EQUAL", filter.getMaxPrice()));

        CompletableFuture.runAsync(() -> {
            try {
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                        "Inventory", ENTITY_VERSION, conditionRequest);

                filteredItemsFuture.thenAccept(filteredItems -> {
                    // Process the filtered items - this logic is now expected to be part of workflow functions
                });

            } catch (Exception e) {
                logger.error("Error fetching inventory data", e);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Job initiated with ID: " + jobId);
    }

    @GetMapping("/report")
    public ResponseEntity<InventoryReport> getReport() {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("Inventory", ENTITY_VERSION);

        InventoryReport report = itemsFuture.thenApply(items -> {
            int totalItems = items.size();
            double averagePrice = 0.0; // Calculate based on items
            int totalValue = 0; // Calculate based on items
            Map<String, Integer> categoryDistribution = Map.of(); // Calculate based on items
            return new InventoryReport(totalItems, averagePrice, totalValue, categoryDistribution);
        }).join();

        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException e) {
        logger.error("Error: {}", e.getMessage());
        return ResponseEntity.status(e.getStatusCode()).body("Error: " + e.getStatusCode().toString());
    }

    public static class InventoryFilter {
        @NotBlank
        private String category;

        @NotNull
        private Integer minPrice;

        @NotNull
        private Integer maxPrice;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Integer getMinPrice() {
            return minPrice;
        }

        public void setMinPrice(Integer minPrice) {
            this.minPrice = minPrice;
        }

        public Integer getMaxPrice() {
            return maxPrice;
        }

        public void setMaxPrice(Integer maxPrice) {
            this.maxPrice = maxPrice;
        }
    }

    public static class InventoryReport {
        private int totalItems;
        private double averagePrice;
        private int totalValue;
        private Map<String, Integer> categoryDistribution;

        public InventoryReport(int totalItems, double averagePrice, int totalValue, Map<String, Integer> categoryDistribution) {
            this.totalItems = totalItems;
            this.averagePrice = averagePrice;
            this.totalValue = totalValue;
            this.categoryDistribution = categoryDistribution;
        }

        public int getTotalItems() {
            return totalItems;
        }

        public void setTotalItems(int totalItems) {
            this.totalItems = totalItems;
        }

        public double getAveragePrice() {
            return averagePrice;
        }

        public void setAveragePrice(double averagePrice) {
            this.averagePrice = averagePrice;
        }

        public int getTotalValue() {
            return totalValue;
        }

        public void setTotalValue(int totalValue) {
            this.totalValue = totalValue;
        }

        public Map<String, Integer> getCategoryDistribution() {
            return categoryDistribution;
        }

        public void setCategoryDistribution(Map<String, Integer> categoryDistribution) {
            this.categoryDistribution = categoryDistribution;
        }
    }
}