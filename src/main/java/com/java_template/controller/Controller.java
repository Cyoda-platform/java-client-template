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
     * Adds the catEvent entity without workflow.
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
                entity
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
     * Adds notification entity without workflow.
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
                entity
        ).thenApply(id -> ResponseEntity.ok(Map.of("status", "sent", "id", id.toString())));
    }

    // --------------------
    // Workflow functions removed
    // --------------------

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