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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/events/detect")
    public ResponseEntity<SuccessResponse> detectEvent(@RequestBody @Valid CatEvent event) {
        logger.info("Received event detection request: {}", event);

        if (event.getCatId() == null || event.getCatId().isBlank()) {
            return ResponseEntity.badRequest().body(new SuccessResponse(false, "catId is required"));
        }

        if (event.getEventType() == null || event.getEventType().isBlank()) {
            return ResponseEntity.badRequest().body(new SuccessResponse(false, "eventType is required"));
        }

        if (event.getEventTimestamp() == null || event.getEventTimestamp().isBlank()) {
            return ResponseEntity.badRequest().body(new SuccessResponse(false, "eventTimestamp is required"));
        }

        // Construct Notification entity with initial data
        Notification notification = new Notification();
        notification.setNotificationId(UUID.randomUUID().toString());
        notification.setCatId(event.getCatId());
        notification.setEventType(event.getEventType());
        notification.setTimestamp(Instant.now().toString());
        notification.setMessage("Emergency! A cat demands snacks.");

        // Persist notification with workflow function that handles async processing before persistence
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "notification",
                ENTITY_VERSION,
                notification,
                this::processNotification
        );

        // Fire and forget, no blocking on idFuture here
        return ResponseEntity.ok(new SuccessResponse(true, "Event received and notification processing started"));
    }

    /**
     * Workflow function to process Notification entity asynchronously prior to persistence.
     * Accepts notification entity as ObjectNode, modifies state directly.
     * Can add/get entities of different entityModels but MUST NOT modify current notification entityModel via entityService.
     * Returns the modified entity for persistence.
     */
    private CompletableFuture<ObjectNode> processNotification(ObjectNode notificationEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Processing notification workflow for entity: {}", notificationEntity);

                // Add processed timestamp to entity
                notificationEntity.put("processedTimestamp", Instant.now().toString());

                // Create supplementary entity for logging purposes
                ObjectNode logEntity = notificationEntity.objectNode();
                logEntity.put("notificationId", notificationEntity.path("notificationId").asText());
                logEntity.put("logMessage", "Notification processed asynchronously");
                logEntity.put("logTimestamp", Instant.now().toString());

                // Add supplementary entity of different entityModel "notification_log"
                entityService.addItem("notification_log", ENTITY_VERSION, logEntity);

                // Additional async logic can be added here if needed

                return notificationEntity;
            } catch (Exception e) {
                logger.error("Error during notification workflow processing", e);
                // Return entity unchanged on error to avoid breaking persistence
                return notificationEntity;
            }
        });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatEvent {
        @NotBlank
        private String catId;

        @NotBlank
        private String eventType;

        @NotBlank
        private String eventTimestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Notification {
        private String notificationId;
        private String catId;
        private String eventType;
        private String timestamp;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuccessResponse {
        private boolean success;
        private String message;
    }
}