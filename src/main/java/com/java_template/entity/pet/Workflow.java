package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    // Main workflow orchestration method - no business logic here
    public CompletableFuture<ObjectNode> processpet(ObjectNode entity) {
        return processValidate(entity)
                .thenCompose(this::processDefaults)
                .thenCompose(this::processEnrichCategory)
                .thenCompose(this::processAsyncLogging);
    }

    // Validate mandatory fields and throw IllegalArgumentException if invalid
    private CompletableFuture<ObjectNode> processValidate(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.hasNonNull("name") || entity.get("name").asText().trim().isEmpty()) {
                throw new IllegalArgumentException("Pet name is required");
            }
            return entity;
        });
    }

    // Set default values for optional fields if missing or empty
    private CompletableFuture<ObjectNode> processDefaults(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.hasNonNull("status") || entity.get("status").asText().trim().isEmpty()) {
                entity.put("status", "available"); // default status
            }
            if (!entity.hasNonNull("category") || entity.get("category").asText().trim().isEmpty()) {
                entity.put("category", "default-category");
            }
            if (!entity.has("photoUrls") || !entity.get("photoUrls").isArray()) {
                entity.putArray("photoUrls");
            }
            if (!entity.has("tags") || !entity.get("tags").isArray()) {
                entity.putArray("tags");
            }
            return entity;
        });
    }

    // Enrich pet entity with category details (mocked here, replace with real async call if needed)
    private CompletableFuture<ObjectNode> processEnrichCategory(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String category = entity.get("category").asText();
                // TODO: Replace with real async call to fetch category details
                ObjectNode categoryDetails = entity.objectNode();
                categoryDetails.put("name", category);
                categoryDetails.put("description", "Sample description for " + category);
                entity.set("categoryDetails", categoryDetails);
                logger.info("Added category details to pet entity");
            } catch (Exception e) {
                logger.warn("Failed to enrich category details", e);
            }
            return entity;
        });
    }

    // Fire-and-forget async logging side effect, no entity modification
    private CompletableFuture<ObjectNode> processAsyncLogging(ObjectNode entity) {
        CompletableFuture.runAsync(() -> {
            try {
                String name = entity.path("name").asText("unknown");
                logger.info("Async logging: new pet to be added: {}", name);
            } catch (Exception e) {
                logger.error("Async logging failed", e);
            }
        });
        return CompletableFuture.completedFuture(entity);
    }
}