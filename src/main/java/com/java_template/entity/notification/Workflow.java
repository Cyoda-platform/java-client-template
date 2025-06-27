package com.java_template.entity.notification;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component("notification")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> start_validation(ObjectNode entity) {
        return validateTimestamp(entity);
    }

    public CompletableFuture<ObjectNode> timestamp_validated(ObjectNode entity) {
        return sendNotificationAsync(entity);
    }

    public CompletableFuture<ObjectNode> validateTimestamp(ObjectNode entity) {
        if (!entity.hasNonNull("timestamp")) {
            entity.put("timestamp", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> sendNotificationAsync(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String message = entity.hasNonNull("message") ? entity.get("message").asText() : "No message";
                String eventType = entity.hasNonNull("eventType") ? entity.get("eventType").asText() : "unknown";
                logger.info("Sending notification asynchronously: [{}], eventType: {}", message, eventType);
                // TODO: Add actual notification sending logic here (email, push, etc.)
                logger.info("Notification sent: {}", message);
            } catch (Exception ex) {
                logger.error("Failed to send notification asynchronously", ex);
            }
            return entity;
        });
    }
}