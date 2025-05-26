package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;

public class Workflow {

    public CompletableFuture<ObjectNode> processNotification(ObjectNode notification) {
        // Orchestrate workflow steps here without business logic
        return processValidateNotification(notification)
                .thenCompose(this::processMarkUnread)
                .thenCompose(this::processFinalize);
    }

    private CompletableFuture<ObjectNode> processValidateNotification(ObjectNode notification) {
        return CompletableFuture.supplyAsync(() -> {
            // Example validation: ensure message field exists
            if (!notification.has("message") || notification.get("message").asText().isEmpty()) {
                notification.put("validationError", "Missing message");
            }
            return notification;
        });
    }

    private CompletableFuture<ObjectNode> processMarkUnread(ObjectNode notification) {
        return CompletableFuture.supplyAsync(() -> {
            // Ensure 'read' attribute is set to false
            notification.put("read", false);
            return notification;
        });
    }

    private CompletableFuture<ObjectNode> processFinalize(ObjectNode notification) {
        return CompletableFuture.completedFuture(notification);
    }
}