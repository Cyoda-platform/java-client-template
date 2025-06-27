package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
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
@RequestMapping("/cyoda/api")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/events/detect")
    public ResponseEntity<EventDetectResponse> detectEvent(@RequestBody @Valid CatEventRequest catEventRequest) {
        logger.info("Received event detection request: {}", catEventRequest);
        if ("dramatic_food_request".equalsIgnoreCase(catEventRequest.getEventType())) {
            sendNotificationAsync("Emergency! A cat demands snacks", catEventRequest.getEventType());
            return ResponseEntity.ok(new EventDetectResponse("notification_sent", "Notification has been sent."));
        }
        logger.info("Event logged but no notification triggered: {}", catEventRequest.getEventType());
        return ResponseEntity.ok(new EventDetectResponse("event_logged", "Event has been logged."));
    }

    @GetMapping("/notifications")
    public CompletableFuture<ResponseEntity<List<Notification>>> getNotifications() {
        logger.info("Fetching all notifications from EntityService");
        return entityService.getItems("notification", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Notification> notifications = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        Notification notification = new Notification();
                        notification.setId(UUID.fromString(node.get("technicalId").asText()));
                        notification.setMessage(node.get("message").asText());
                        notification.setEventType(node.get("eventType").asText());
                        notification.setTimestamp(Instant.parse(node.get("timestamp").asText()));
                        notifications.add(notification);
                    });
                    notifications.sort(Comparator.comparing(Notification::getTimestamp).reversed());
                    return ResponseEntity.ok(notifications);
                });
    }

    @PostMapping("/notifications/manual")
    public ResponseEntity<ManualNotificationResponse> manualNotification(@RequestBody @Valid ManualNotificationRequest request) {
        logger.info("Manual notification request received: {}", request);
        sendNotificationAsync(request.getMessage(), "manual_override");
        return ResponseEntity.ok(new ManualNotificationResponse("sent", "Notification sent manually."));
    }

    @Async
    void sendNotificationAsync(String message, String eventType) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Sending notification: [{}], eventType: {}", message, eventType);
                Notification notification = new Notification(null, message, eventType, Instant.now());
                entityService.addItem("notification", ENTITY_VERSION, notification).join();
                logger.info("Notification stored via EntityService");
            } catch (Exception ex) {
                logger.error("Failed to send notification asynchronously", ex);
            }
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unhandled exception caught", ex);
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

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
    public static class EventDetectResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Notification {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId; // to avoid confusion with id in EntityService
        private UUID id;
        private String message;
        private String eventType;
        private Instant timestamp;

        public void setId(UUID id) {
            this.id = id;
            this.technicalId = id;
        }

        public UUID getId() {
            return technicalId;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualNotificationRequest {
        @NotBlank
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualNotificationResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}