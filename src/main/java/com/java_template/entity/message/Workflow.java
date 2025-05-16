package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    public CompletableFuture<ObjectNode> processMessage(ObjectNode entity) {
        // Orchestrate workflow steps
        return processSetTimestamp(entity)
                .thenCompose(this::processAsyncLogging)
                .thenCompose(this::processAdditionalLogic);
    }

    private CompletableFuture<ObjectNode> processSetTimestamp(ObjectNode entity) {
        if (!entity.hasNonNull("timestamp")) {
            entity.put("timestamp", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processAsyncLogging(ObjectNode entity) {
        CompletableFuture.runAsync(() -> logger.info("Async processing for message entity before persistence: {}", entity.toString()));
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processAdditionalLogic(ObjectNode entity) {
        // TODO: Add any additional business logic here without modifying other entities via add/update/delete
        return CompletableFuture.completedFuture(entity);
    }
}