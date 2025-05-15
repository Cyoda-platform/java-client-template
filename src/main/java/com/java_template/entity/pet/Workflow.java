package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class EntityWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(EntityWorkflow.class);

    public CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        // Workflow orchestration only
        return processValidateDescription(entity)
                .thenCompose(this::processTriggerWorkflow);
    }

    private CompletableFuture<ObjectNode> processValidateDescription(ObjectNode entity) {
        if (!entity.hasNonNull("description") || entity.get("description").asText().isBlank()) {
            entity.put("description", "No description provided.");
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processTriggerWorkflow(ObjectNode entity) {
        CompletableFuture.runAsync(() -> {
            String technicalId = entity.hasNonNull("technicalId") ? entity.get("technicalId").asText() : "<unknown>";
            logger.info("Workflow triggered for pet technicalId={} at {}", technicalId, Instant.now());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            logger.info("Workflow completed for pet technicalId={}", technicalId);
        });
        return CompletableFuture.completedFuture(entity);
    }
}