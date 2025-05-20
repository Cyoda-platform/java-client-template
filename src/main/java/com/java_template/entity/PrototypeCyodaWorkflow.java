package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-products")
public class CyodaEntityController {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityController(EntityService entityService) {
        this.entityService = entityService;
    }

    public static class ScrapeRequest {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
    }

    /**
     * Workflow function for "cyodaproduct" entityModel.
     * This function asynchronously performs scraping, analysis,
     * creates supplementary "productitem" entities,
     * modifies this entity state with summary and status,
     * and returns the modified entity to be persisted.
     *
     * @param entity ObjectNode representing the entity data being added.
     * @return CompletableFuture<ObjectNode> asynchronously returning modified entity.
     */
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processcyodaproduct = entity -> {
        log.info("Workflow processcyodaproduct started for entity: {}", entity);
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate minimal required fields if any (e.g. username)
                if (!entity.hasNonNull("username") || entity.get("username").asText().isBlank()) {
                    throw new IllegalArgumentException("Missing required field: username");
                }

                // 1) Perform scraping and analysis - example mock call
                List<ObjectNode> productItems = scrapeAndParseProducts();

                // 2) Compute summary report from productItems
                ObjectNode summaryReport = createSummaryReport(productItems);

                // 3) Save productItems as separate entities (different entityModel)
                // Use workflow function for productitem entities as well.
                entityService.addItems(
                        "productitem",
                        ENTITY_VERSION,
                        productItems,
                        this::processproductitem // workflow for productitem entities
                ).join();

                // 4) Update current entity state (e.g. status, summary, timestamps)
                entity.put("status", "completed");
                entity.set("summaryReport", summaryReport);
                entity.put("updatedAt", Instant.now().toString());

                return entity;

            } catch (Exception ex) {
                log.error("Error in workflow processcyodaproduct", ex);
                // Mark entity as failed with error details but do not throw, so entity is persisted with error state
                entity.put("status", "failed");
                entity.put("errorMessage", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
                entity.put("updatedAt", Instant.now().toString());
                return entity;
            }
        });
    };

    /**
     * Workflow function for "productitem" entityModel.
     * Currently no-op, but you can add validation or modify productitem state here.
     * Must return CompletableFuture of modified entity.
     */
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processproductitem = productItem -> {
        // Example validation: ensure price and inventory are present and valid
        if (!productItem.hasNonNull("price") || productItem.get("price").asDouble(-1.0) < 0) {
            productItem.put("price", 0.0);
        }
        if (!productItem.hasNonNull("inventory") || productItem.get("inventory").asInt(-1) < 0) {
            productItem.put("inventory", 0);
        }
        return CompletableFuture.completedFuture(productItem);
    };

    /**
     * Controller endpoint - now minimal.
     * Creates a raw cyodaproduct entity with username and status "processing".
     * Calls entityService.addItem with workflow function to perform async scraping and processing.
     */
    @PostMapping("/scrape")
    public CompletableFuture<ResponseEntity<String>> scrapeProducts(@RequestBody ScrapeRequest request) {
        // Create initial entity node with minimal info
        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("username", request.username);
        entityNode.put("requestedAt", Instant.now().toString());
        entityNode.put("status", "processing");

        return entityService.addItem(
                "cyodaproduct",
                ENTITY_VERSION,
                entityNode,
                processcyodaproduct
        ).thenApply(id -> ResponseEntity.ok("Scraping started with job id: " + id));
    }

    /**
     * Helper method to scrape and parse products.
     * Replace with real scraping and parsing logic.
     * Returns list of productitem entities as ObjectNode.
     */
    private List<ObjectNode> scrapeAndParseProducts() {
        log.info("Scraping and parsing products (mock implementation)");

        List<ObjectNode> products = new ArrayList<>();

        ObjectNode p1 = objectMapper.createObjectNode();
        p1.put("name", "Sauce Labs Backpack");
        p1.put("description", "carry all the things");
        p1.put("price", 29.99);
        p1.put("inventory", 1);
        products.add(p1);

        ObjectNode p2 = objectMapper.createObjectNode();
        p2.put("name", "Sauce Labs Bike Light");
        p2.put("description", "a light for your bike");
        p2.put("price", 9.99);
        p2.put("inventory", 1);
        products.add(p2);

        ObjectNode p3 = objectMapper.createObjectNode();
        p3.put("name", "Sauce Labs Bolt T-Shirt");
        p3.put("description", "soft and comfortable");
        p3.put("price", 15.99);
        p3.put("inventory", 1);
        products.add(p3);

        ObjectNode p4 = objectMapper.createObjectNode();
        p4.put("name", "Sauce Labs Fleece Jacket");
        p4.put("description", "warm fleece jacket");
        p4.put("price", 49.99);
        p4.put("inventory", 1);
        products.add(p4);

        return products;
    }

    /**
     * Create summary report JSON node from product items.
     */
    private ObjectNode createSummaryReport(List<ObjectNode> products) {
        ObjectNode report = objectMapper.createObjectNode();

        int totalProducts = products.size();
        double totalValue = 0.0;
        ObjectNode highest = null;
        ObjectNode lowest = null;

        for (ObjectNode p : products) {
            double price = p.get("price").asDouble();
            int inventory = p.get("inventory").asInt();
            totalValue += price * inventory;
            if (highest == null || price > highest.get("price").asDouble()) highest = p;
            if (lowest == null || price < lowest.get("price").asDouble()) lowest = p;
        }

        double avgPrice = totalProducts > 0 ? totalValue / totalProducts : 0;

        report.put("totalProducts", totalProducts);
        report.put("averagePrice", round(avgPrice));
        report.set("highestPricedItem", highest != null ? highest.deepCopy() : null);
        report.set("lowestPricedItem", lowest != null ? lowest.deepCopy() : null);
        report.put("totalInventoryValue", round(totalValue));
        report.put("generatedAt", Instant.now().toString());

        return report;
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}