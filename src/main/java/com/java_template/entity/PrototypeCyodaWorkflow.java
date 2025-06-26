package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/events/detect")
    public CompletableFuture<ResponseEntity<EventDetectResponse>> detectEvent(@RequestBody @Valid EventDetectRequest request) {
        try {
            ObjectNode eventEntity = objectMapper.valueToTree(request);
            return entityService.addItem("event", ENTITY_VERSION, eventEntity, this::processEvent)
                    .thenApply(id -> {
                        boolean detected = eventEntity.path("detected").asBoolean(false);
                        String message = eventEntity.path("message").asText("");
                        return ResponseEntity.ok(new EventDetectResponse(detected, message));
                    });
        } catch (Exception e) {
            log.error("Error processing detectEvent request", e);
            CompletableFuture<ResponseEntity<EventDetectResponse>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    @GetMapping("/notifications")
    public CompletableFuture<ResponseEntity<List<NotificationRecord>>> getNotifications() {
        log.info("Retrieving all notifications");
        return entityService.getItems("notification", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<NotificationRecord> notifications = new ArrayList<>();
                    arrayNode.forEach(jsonNode -> {
                        NotificationRecord record = null;
                        try {
                            record = objectMapper.treeToValue(jsonNode, NotificationRecord.class);
                        } catch (Exception e) {
                            log.error("Failed to parse NotificationRecord from ObjectNode", e);
                        }
                        if (record != null) {
                            notifications.add(record);
                        }
                    });
                    notifications.sort(Comparator.comparing(NotificationRecord::getTimestamp).reversed());
                    return ResponseEntity.ok(notifications);
                });
    }

    @PostMapping("/notifications/send")
    public CompletableFuture<ResponseEntity<NotificationSendResponse>> sendNotification(@RequestBody @Valid NotificationSendRequest request) {
        try {
            NotificationRecord notificationRecord = new NotificationRecord(null, request.getMessage(), Instant.now());
            ObjectNode notificationEntity = objectMapper.valueToTree(notificationRecord);
            // Add recipient info to entity for workflow usage
            notificationEntity.put("recipient", request.getRecipient());
            return entityService.addItem("notification", ENTITY_VERSION, notificationEntity, this::processNotification)
                    .thenApply(id -> ResponseEntity.ok(new NotificationSendResponse("sent", "Notification sent successfully")));
        } catch (Exception e) {
            log.error("Error sending notification", e);
            CompletableFuture<ResponseEntity<NotificationSendResponse>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    // Workflow function for "event" entity
    private CompletableFuture<ObjectNode> processEvent(ObjectNode eventEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String eventType = eventEntity.path("eventType").asText("");
                String timestamp = eventEntity.path("timestamp").asText("");
                log.info("Processing event entity in workflow: eventType='{}', timestamp='{}'", eventType, timestamp);

                boolean isKeyEvent = "food_request".equalsIgnoreCase(eventType);
                eventEntity.put("detected", isKeyEvent);

                if (isKeyEvent) {
                    String notificationMsg = "Emergency! A cat demands snacks";
                    eventEntity.put("message", notificationMsg);

                    ObjectNode notificationEntity = objectMapper.createObjectNode();
                    notificationEntity.put("message", notificationMsg);
                    notificationEntity.put("timestamp", Instant.now().toString());
                    notificationEntity.put("recipient", "default_human_recipient@example.com");

                    // Add notification entity asynchronously without workflow (identity function)
                    entityService.addItem("notification", ENTITY_VERSION, notificationEntity, n -> n)
                            .thenAccept(id -> log.info("Notification created by event workflow with id={}", id))
                            .exceptionally(ex -> {
                                log.error("Failed to add notification entity in event workflow", ex);
                                return null;
                            });
                } else {
                    eventEntity.put("message", "");
                }

                return eventEntity;
            } catch (Exception e) {
                log.error("Exception in processEvent workflow", e);
                return eventEntity;
            }
        });
    }

    // Workflow function for "notification" entity
    private CompletableFuture<ObjectNode> processNotification(ObjectNode notificationEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String originalMessage = notificationEntity.path("message").asText("");
                notificationEntity.put("message", "[Processed] " + originalMessage);

                // Extract recipient to simulate sending
                String recipient = notificationEntity.path("recipient").asText("");
                if (recipient.isEmpty()) {
                    log.warn("Notification recipient is empty, cannot send notification");
                } else {
                    // Simulate async sending logic here (fire and forget)
                    log.info("Sending notification asynchronously to '{}': {}", recipient, originalMessage);
                    // Insert real sending logic here, e.g. HTTP call, message queue, etc.
                }

                return notificationEntity;
            } catch (Exception e) {
                log.error("Exception in processNotification workflow", e);
                return notificationEntity;
            }
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected exception: ", ex);
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDetectRequest {
        @NotBlank
        private String eventType;
        @NotBlank
        private String eventData;
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T.+$", message = "timestamp must be ISO8601")
        private String timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDetectResponse {
        private boolean detected;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationRecord {
        private UUID id;
        private String message;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSendRequest {
        @NotBlank
        @Size(max = 255)
        private String message;
        @NotBlank
        private String recipient;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSendResponse {
        private String status;
        private String details;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}