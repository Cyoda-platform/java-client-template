package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda/api")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Endpoint to receive cat events.
     * Adds the catEvent entity with workflow processCatEvent.
     */
    @PostMapping("/events/detect")
    public CompletableFuture<ResponseEntity<Map<String, String>>> detectEvent(@RequestBody @Valid CatEventRequest catEventRequest) {
        logger.info("Received event detection request: {}", catEventRequest);
        ObjectNode entity = entityService.getObjectMapper().valueToTree(catEventRequest);
        // Defensive: ensure required fields present
        if (!entity.hasNonNull("eventType") || !entity.hasNonNull("timestamp")) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body(Map.of("error", "Missing required eventType or timestamp")));
        }
        return entityService.addItem(
                "catEvent",
                ENTITY_VERSION,
                entity,
                this::processCatEvent
        ).thenApply(id -> ResponseEntity.ok(Map.of("status", "processed", "id", id.toString())));
    }

    /**
     * Endpoint to get all notifications.
     */
    @GetMapping("/notifications")
    public CompletableFuture<ResponseEntity<List<Notification>>> getNotifications() {
        logger.info("Fetching all notifications from EntityService");
        return entityService.getItems("notification", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Notification> notifications = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        try {
                            Notification notification = new Notification();
                            notification.setId(UUID.fromString(node.get("technicalId").asText()));
                            notification.setMessage(node.get("message").asText());
                            notification.setEventType(node.get("eventType").asText());
                            notification.setTimestamp(Instant.parse(node.get("timestamp").asText()));
                            notifications.add(notification);
                        } catch (Exception e) {
                            logger.warn("Skipping invalid notification entity: {}", node, e);
                        }
                    });
                    notifications.sort(Comparator.comparing(Notification::getTimestamp).reversed());
                    return ResponseEntity.ok(notifications);
                });
    }

    /**
     * Endpoint for manual notification.
     * Adds notification entity with workflow processNotification.
     */
    @PostMapping("/notifications/manual")
    public CompletableFuture<ResponseEntity<Map<String, String>>> manualNotification(@RequestBody @Valid ManualNotificationRequest request) {
        logger.info("Manual notification request received: {}", request);
        ObjectNode entity = entityService.getObjectMapper().valueToTree(request);
        entity.put("eventType", "manual_override");
        entity.put("timestamp", Instant.now().toString());
        return entityService.addItem(
                "notification",
                ENTITY_VERSION,
                entity,
                this::processNotification
        ).thenApply(id -> ResponseEntity.ok(Map.of("status", "sent", "id", id.toString())));
    }

    // --------------------
    // Workflow functions
    // --------------------

    /**
     * Workflow function for catEvent entity.
     * If eventType == "dramatic_food_request", triggers notification asynchronously.
     * Returns original catEvent entity.
     */
    private CompletableFuture<ObjectNode> processCatEvent(ObjectNode catEventEntity) {
        String eventType = catEventEntity.has("eventType") && !catEventEntity.get("eventType").isNull()
                ? catEventEntity.get("eventType").asText()
                : null;
        logger.info("Processing CatEvent workflow: eventType={}", eventType);
        if ("dramatic_food_request".equalsIgnoreCase(eventType)) {
            ObjectNode notification = entityService.getObjectMapper().createObjectNode();
            notification.put("message", "Emergency! A cat demands snacks");
            notification.put("eventType", eventType);
            notification.put("timestamp", Instant.now().toString());
            // Add notification with its workflow that sends it asynchronously
            return entityService.addItem(
                    "notification",
                    ENTITY_VERSION,
                    notification,
                    this::processNotification
            ).thenApply(uuid -> catEventEntity);
        }
        return CompletableFuture.completedFuture(catEventEntity);
    }

    /**
     * Workflow function for notification entity.
     * Ensures timestamp is present.
     * Fire-and-forget async sending of notification.
     * Returns notification entity unmodified.
     */
    private CompletableFuture<ObjectNode> processNotification(ObjectNode notificationEntity) {
        try {
            if (!notificationEntity.hasNonNull("timestamp")) {
                notificationEntity.put("timestamp", Instant.now().toString());
            }
            // Async fire-and-forget notification sender
            CompletableFuture.runAsync(() -> {
                try {
                    String message = notificationEntity.hasNonNull("message") ? notificationEntity.get("message").asText() : "No message";
                    String eventType = notificationEntity.hasNonNull("eventType") ? notificationEntity.get("eventType").asText() : "unknown";
                    logger.info("Sending notification asynchronously: [{}], eventType: {}", message, eventType);
                    // Place actual notification sending logic here, e.g. email, push, etc.
                    logger.info("Notification sent: {}", message);
                } catch (Exception ex) {
                    logger.error("Failed to send notification asynchronously", ex);
                }
            });
        } catch (Exception ex) {
            logger.error("Exception in processNotification workflow", ex);
        }
        return CompletableFuture.completedFuture(notificationEntity);
    }

    // --------------------
    // DTO classes
    // --------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatEventRequest {
        @NotBlank
        private String eventType;
        @NotBlank
        private String timestamp;
        private String intensity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Notification {
        private UUID id;
        private String message;
        private String eventType;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualNotificationRequest {
        @NotBlank
        private String message;
    }

}