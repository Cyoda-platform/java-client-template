package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        logger.info("Starting processPet workflow for entity: {}", petEntity);

        // Orchestration of steps without business logic here
        return processSetDefaultStatus(petEntity)
                .thenCompose(e -> processSetCreatedAt(e))
                .thenCompose(e -> processSetDefaultTags(e))
                .thenCompose(e -> processEnrichCategory(e))
                .thenCompose(e -> processFireAndForget(e));
    }

    private CompletableFuture<ObjectNode> processSetDefaultStatus(ObjectNode entity) {
        if (!entity.hasNonNull("status") || entity.get("status").asText().isBlank()) {
            entity.put("status", "available");
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetCreatedAt(ObjectNode entity) {
        if (!entity.hasNonNull("createdAt")) {
            entity.put("createdAt", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetDefaultTags(ObjectNode entity) {
        if (!entity.has("tags") || !entity.get("tags").isArray() || entity.get("tags").size() == 0) {
            ArrayNode tagsArray = entity.putArray("tags");
            tagsArray.add("new-pet");
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processEnrichCategory(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String type = entity.hasNonNull("type") ? entity.get("type").asText() : null;
                if (type != null && !type.isBlank()) {
                    logger.info("Would enrich pet with category entity for type '{}'", type);
                    // TODO: fetch and enrich category entity here if needed
                }
            } catch (Exception e) {
                logger.warn("Failed to enrich category in processEnrichCategory", e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processFireAndForget(ObjectNode entity) {
        // Fire and forget async task, no entity state changes here
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Fire and forget async task: audit log for pet: {}", entity);
                // TODO: send audit log or notification asynchronously
            } catch (Exception e) {
                logger.warn("Fire and forget task failed", e);
            }
        });
        return CompletableFuture.completedFuture(entity);
    }
}