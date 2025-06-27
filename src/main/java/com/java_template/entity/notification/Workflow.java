package com.java_template.entity.notification;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("notification")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> processNotification(ObjectNode entity) {
        try {
            // Workflow orchestration only - no business logic here
            return CompletableFuture.completedFuture(entity)
                    .thenCompose(this::validateTimestamp)
                    .thenCompose(this::sendNotificationAsync);
        } catch (Exception ex) {
            logger.error("Exception in processNotification workflow", ex);
            return CompletableFuture.completedFuture(entity);
        }
    }

    private CompletableFuture<ObjectNode> validateTimestamp(ObjectNode entity) {
        if (!entity.hasNonNull("timestamp")) {
            entity.put("timestamp", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> sendNotificationAsync(ObjectNode entity) {
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