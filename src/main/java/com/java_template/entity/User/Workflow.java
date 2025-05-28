package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Workflow {
    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> processUser(ObjectNode userEntity) {
        return processIngestion(userEntity)
                .thenCompose(this::processTransformation)
                .thenCompose(this::processStorage)
                .thenCompose(this::processReporting)
                .thenCompose(this::processPublishing)
                .thenApply(entity -> {
                    entity.put("processedAt", OffsetDateTime.now().toString());
                    return entity;
                });
    }

    private CompletableFuture<ObjectNode> processIngestion(ObjectNode entity) {
        // Example: simulate fetching from external source (Fakerest API)
        // Since entity is passed in, assume ingestion means adding a timestamp or flag
        entity.put("ingestedAt", OffsetDateTime.now().toString());
        logger.info("Ingestion completed for id={}", entity.path("id").asText(""));
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processTransformation(ObjectNode entity) {
        // Apply any transformation logic, e.g. normalize or enrich fields
        String userName = entity.path("userName").asText("");
        entity.put("userName", userName.trim().toLowerCase());
        logger.info("Transformation completed for id={}, userName={}", entity.path("id").asText(""), entity.path("userName").asText(""));
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processStorage(ObjectNode entity) {
        // Simulate storage by adding a stored flag or timestamp
        entity.put("storedAt", OffsetDateTime.now().toString());
        logger.info("Storage completed for id={}", entity.path("id").asText(""));
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processReporting(ObjectNode entity) {
        // Simulate report generation flag or timestamp
        entity.put("reportedAt", OffsetDateTime.now().toString());
        logger.info("Reporting completed for id={}", entity.path("id").asText(""));
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processPublishing(ObjectNode entity) {
        // Simulate publishing by adding a publishedAt timestamp
        entity.put("publishedAt", OffsetDateTime.now().toString());

        // Fire-and-forget async side effect - logging or external call simulation
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Publishing side effect for id={}, userName={}", entity.path("id").asText(""), entity.path("userName").asText(""));
                // TODO: Add actual publishing logic here (e.g. email sending)
            } catch (Exception e) {
                logger.warn("Exception in async side effect of processPublishing: ", e);
            }
        });

        return CompletableFuture.completedFuture(entity);
    }
}