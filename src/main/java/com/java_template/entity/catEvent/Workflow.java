package com.java_template.entity.catEvent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("catEvent")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private EntityService entityService;

    // Workflow orchestration only - no business logic here
    public CompletableFuture<ObjectNode> processCatEvent(ObjectNode catEventEntity) {
        String eventType = extractEventType(catEventEntity);
        logger.info("Processing CatEvent workflow: eventType={}", eventType);

        if ("dramatic_food_request".equalsIgnoreCase(eventType)) {
            return handleDramaticFoodRequest(catEventEntity, eventType);
        }

        return CompletableFuture.completedFuture(catEventEntity);
    }

    // Extract eventType from entity
    private String extractEventType(ObjectNode entity) {
        if (entity.has("eventType") && !entity.get("eventType").isNull()) {
            return entity.get("eventType").asText();
        }
        return null;
    }

    // Business logic separated: creates notification entity and triggers its workflow
    private CompletableFuture<ObjectNode> handleDramaticFoodRequest(ObjectNode catEventEntity, String eventType) {
        ObjectNode notification = createNotification(eventType);
        // addItem on notification entity; when done, return original catEventEntity
        return entityService.addItem(
                "notification",
                ENTITY_VERSION,
                notification,
                this::processNotification
        ).thenApply(uuid -> catEventEntity);
    }

    // Business logic separated: create notification object
    private ObjectNode createNotification(String eventType) {
        ObjectNode notification = entityService.getObjectMapper().createObjectNode();
        notification.put("message", "Emergency! A cat demands snacks");
        notification.put("eventType", eventType);
        notification.put("timestamp", Instant.now().toString());
        return notification;
    }

    // Placeholder for notification workflow
    private CompletableFuture<ObjectNode> processNotification(ObjectNode notificationEntity) {
        // TODO: implement notification sending workflow here
        logger.info("Processing Notification workflow for id={}", notificationEntity.get("id"));
        return CompletableFuture.completedFuture(notificationEntity);
    }
}