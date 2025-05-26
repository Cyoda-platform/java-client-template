package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class Workflow {

    public CompletableFuture<ObjectNode> processComment(ObjectNode comment) {
        return processTimestamp(comment)
                .thenCompose(this::processModeration)
                .thenCompose(this::processSpamDetection)
                .thenApply(entity -> {
                    // Orchestration only, no business logic here
                    return entity;
                });
    }

    private CompletableFuture<ObjectNode> processTimestamp(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.has("timestamp") || entity.path("timestamp").isNull()) {
                entity.put("timestamp", Instant.now().toString());
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processModeration(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Add moderation logic here, for now just pass through
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSpamDetection(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Add spam detection logic here, for now just pass through
            return entity;
        });
    }
}