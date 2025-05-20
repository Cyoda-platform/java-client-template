package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    public CompletableFuture<ObjectNode> processsubscriber(ObjectNode entity) {
        // Workflow orchestration only
        return processSubscribe(entity)
                .thenCompose(this::processUnsubscribeCheck)
                .thenCompose(this::processSendCatFact)
                .thenCompose(this::processTrackEmailOpen);
    }

    // Set subscribedAt if missing
    public CompletableFuture<ObjectNode> processSubscribe(ObjectNode entity) {
        if (!entity.hasNonNull("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Check if unsubscribe flag present; if yes, set unsubscribedAt timestamp
    public CompletableFuture<ObjectNode> processUnsubscribeCheck(ObjectNode entity) {
        if (entity.has("unsubscribeRequest") && entity.get("unsubscribeRequest").asBoolean(false)) {
            if (!entity.hasNonNull("unsubscribedAt")) {
                entity.put("unsubscribedAt", Instant.now().toString());
            }
            // Remove unsubscribeRequest flag
            entity.remove("unsubscribeRequest");
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Process sending the cat fact (placeholder logic)
    public CompletableFuture<ObjectNode> processSendCatFact(ObjectNode entity) {
        // Simulate setting lastSentCatFact and lastSentAt timestamp
        if (!entity.hasNonNull("lastSentCatFact")) {
            entity.put("lastSentCatFact", "Cats have five toes on front paws.");
            entity.put("lastSentAt", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Process tracking email opens (increment count)
    public CompletableFuture<ObjectNode> processTrackEmailOpen(ObjectNode entity) {
        int opens = entity.has("emailOpens") ? entity.get("emailOpens").asInt(0) : 0;
        entity.put("emailOpens", opens + 1);
        return CompletableFuture.completedFuture(entity);
    }
}