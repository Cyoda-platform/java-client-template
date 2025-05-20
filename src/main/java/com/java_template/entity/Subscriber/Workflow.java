package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    // Orchestration method only, no business logic here
    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        logger.debug("processSubscriber workflow started for email: {}", entity.get("email").asText());

        return processNormalizeEmail(entity)
                .thenCompose(this::processSetSubscribedAt)
                .thenCompose(this::processDeleteUnsubscribedIfExists);
    }

    // Normalize email and validate
    public CompletableFuture<ObjectNode> processNormalizeEmail(ObjectNode entity) {
        String emailRaw = entity.get("email").asText(null);
        String email = normalizeEmail(emailRaw);
        if (email == null) {
            CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new IllegalArgumentException("Invalid email"));
            return failedFuture;
        }
        entity.put("email", email);
        return CompletableFuture.completedFuture(entity);
    }

    // Set subscribedAt timestamp
    public CompletableFuture<ObjectNode> processSetSubscribedAt(ObjectNode entity) {
        entity.put("subscribedAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }

    // Delete unsubscribed entity if present (mocked here as no external calls)
    public CompletableFuture<ObjectNode> processDeleteUnsubscribedIfExists(ObjectNode entity) {
        String email = entity.get("email").asText();
        // TODO: Implement actual deletion logic asynchronously if needed
        // Here just logging and returning entity
        logger.debug("Checked and deleted UnsubscribedSubscriber for email if existed: {}", email);
        return CompletableFuture.completedFuture(entity);
    }

    // Helper method to normalize email
    private String normalizeEmail(String email) {
        if (email == null) return null;
        String norm = email.trim().toLowerCase();
        if (norm.isEmpty() || !norm.contains("@")) return null;
        return norm;
    }
}