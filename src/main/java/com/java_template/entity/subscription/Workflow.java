package com.java_template.entity.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("subscription")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Action function: processGameScore - normalize status field to uppercase
    public CompletableFuture<ObjectNode> processGameScore(ObjectNode entity) {
        if (entity.has("status") && !entity.get("status").isNull()) {
            String status = entity.get("status").asText();
            entity.put("status", status.toUpperCase());
            logger.info("Processed game score status to uppercase: {}", status.toUpperCase());
        } else {
            logger.info("No status field to process");
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Action function: storeEntity - mock storing entity (can be expanded)
    public CompletableFuture<ObjectNode> storeEntity(ObjectNode entity) {
        logger.info("Entity stored with id: {}", entity.has("id") ? entity.get("id").asText() : "unknown");
        // TODO: Implement actual persistence logic if needed
        return CompletableFuture.completedFuture(entity);
    }

    // Action function: sendNotifications - mock sending notifications
    public CompletableFuture<ObjectNode> sendNotifications(ObjectNode entity) {
        logger.info("Notifications sent for entity id: {}", entity.has("id") ? entity.get("id").asText() : "unknown");
        // TODO: Implement actual notification sending logic
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: hasStatusField - returns true if entity has non-null and non-blank status field
    public boolean hasStatusField(ObjectNode entity) {
        boolean result = entity.has("status") && !entity.get("status").isNull() && !entity.get("status").asText().isBlank();
        logger.info("hasStatusField check: {}", result);
        return result;
    }

    // Condition function: hasNoStatusField - returns true if entity has no status field or it is null/blank
    public boolean hasNoStatusField(ObjectNode entity) {
        boolean result = !entity.has("status") || entity.get("status").isNull() || entity.get("status").asText().isBlank();
        logger.info("hasNoStatusField check: {}", result);
        return result;
    }

    // Condition function: isStatusUppercase - true if status field equals its uppercase version
    public boolean isStatusUppercase(ObjectNode entity) {
        if (!entity.has("status") || entity.get("status").isNull()) {
            logger.info("isStatusUppercase check: false (no status field)");
            return false;
        }
        String status = entity.get("status").asText();
        boolean result = status.equals(status.toUpperCase());
        logger.info("isStatusUppercase check: {}", result);
        return result;
    }

    // Condition function: isStatusNotUppercase - true if status field is present but not uppercase
    public boolean isStatusNotUppercase(ObjectNode entity) {
        if (!entity.has("status") || entity.get("status").isNull()) {
            logger.info("isStatusNotUppercase check: false (no status field)");
            return false;
        }
        String status = entity.get("status").asText();
        boolean result = !status.equals(status.toUpperCase());
        logger.info("isStatusNotUppercase check: {}", result);
        return result;
    }

    // Condition function: isStored - mock check if entity is stored (always true here)
    public boolean isStored(ObjectNode entity) {
        logger.info("isStored check: true (mock)");
        return true;
    }

    // Condition function: isNotStored - mock check if entity is not stored (always false here)
    public boolean isNotStored(ObjectNode entity) {
        logger.info("isNotStored check: false (mock)");
        return false;
    }

    // Condition function: notificationsSent - mock check if notifications sent (always true here)
    public boolean notificationsSent(ObjectNode entity) {
        logger.info("notificationsSent check: true (mock)");
        return true;
    }

    // Condition function: notificationsNotSent - mock check if notifications not sent (always false here)
    public boolean notificationsNotSent(ObjectNode entity) {
        logger.info("notificationsNotSent check: false (mock)");
        return false;
    }
}