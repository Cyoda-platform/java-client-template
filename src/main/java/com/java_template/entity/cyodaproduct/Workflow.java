package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Workflow {
    private static final Logger log = LoggerFactory.getLogger(Workflow.class);
    private static final String ENTITY_VERSION = "1.0";

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processcyodaproduct(ObjectNode entity) {
        log.info("Workflow processcyodaproduct started for entity: {}", entity);
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!entity.hasNonNull("username") || entity.get("username").asText().isBlank()) {
                    throw new IllegalArgumentException("Missing required field: username");
                }
                return processScrapeAndAnalysis(entity)
                        .thenCompose(this::processCreateSummary)
                        .thenCompose(this::processSaveProductItems)
                        .thenApply(this::processUpdateEntityState)
                        .join();
            } catch (Exception ex) {
                log.error("Error in workflow processcyodaproduct", ex);
                entity.put("status", "failed");
                entity.put("errorMessage", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
                entity.put("updatedAt", Instant.now().toString());
                return entity;
            }
        });
    }

    private CompletableFuture<ObjectNode> processScrapeAndAnalysis(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Replace scrapeAndParseProducts with actual scraping logic
            List<ObjectNode> productItems = scrapeAndParseProducts();
            // Store productItems temporarily in entity for next step
            entity.set("productItems", objectMapper.valueToTree(productItems));
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processCreateSummary(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            List<ObjectNode> productItems = objectMapper.convertValue(entity.get("productItems"), List.class);
            ObjectNode summaryReport = createSummaryReport(productItems);
            entity.set("summaryReport", summaryReport);
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSaveProductItems(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            List<ObjectNode> productItems = objectMapper.convertValue(entity.get("productItems"), List.class);
            entityService.addItems(
                    "productitem",
                    ENTITY_VERSION,
                    productItems,
                    this::processproductitem // workflow for productitem entities
            ).join();
            return entity;
        });
    }

    private ObjectNode processUpdateEntityState(ObjectNode entity) {
        entity.put("status", "completed");
        entity.put("updatedAt", Instant.now().toString());
        entity.remove("productItems"); // clean up temporary data
        return entity;
    }

    // Placeholder for scraping and parsing products
    private List<ObjectNode> scrapeAndParseProducts() {
        // TODO: Implement actual scraping and parsing logic
        return List.of();
    }

    // Placeholder for creating summary report
    private ObjectNode createSummaryReport(List<ObjectNode> products) {
        // TODO: Implement actual summary report creation logic
        return objectMapper.createObjectNode();
    }

    // Placeholder for productitem workflow
    private CompletableFuture<ObjectNode> processproductitem(ObjectNode entity) {
        // TODO: Implement actual productitem workflow logic
        return CompletableFuture.completedFuture(entity);
    }
}