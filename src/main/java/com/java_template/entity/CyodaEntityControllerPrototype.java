```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    } 

    @PostMapping("/fetch")
    public ResponseEntity<String> fetchInventoryData(@RequestBody @Valid InventoryFilter filter) {
        String jobId = "job_" + System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            try {
                // Create a search condition based on the filter
                SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND",
                        Condition.of("$.category", "EQUALS", filter.getCategory()),
                        Condition.of("$.price", "GREATER_OR_EQUAL", filter.getMinPrice()),
                        Condition.of("$.price", "LESS_OR_EQUAL", filter.getMaxPrice()));

                // Call the external service to get filtered items
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                        "Inventory", ENTITY_VERSION, conditionRequest);

                // Process the response (example logic)
                filteredItemsFuture.thenAccept(filteredItems -> {
                    // Process the filtered items and populate inventoryData
                    // This is where you would handle the retrieved data
                });

            } catch (Exception e) {
                logger.error("Error fetching inventory data", e);
                // Handle failure scenario, e.g., updating job status
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Job initiated with ID: " + jobId);
    }

    @GetMapping("/report")
    public ResponseEntity<InventoryReport> getReport() {
        // Example logic to retrieve a report using the entity service
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("Inventory", ENTITY_VERSION);

        InventoryReport report = itemsFuture.thenApply(items -> {
            // Process items to generate report
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InventoryFilter {
        @NotBlank
        private String category;

        @NotNull
        private Integer minPrice;

        @NotNull
        private Integer maxPrice;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InventoryReport {
        private int totalItems;
        private double averagePrice;
        private int totalValue;
        private Map<String, Integer> categoryDistribution;
    }
}
```