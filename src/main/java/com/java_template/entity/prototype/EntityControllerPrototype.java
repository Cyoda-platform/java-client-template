```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/api")
public class EntityControllerPrototype {

    private final Map<UUID, Notification> notifications = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * POST /api/events/detect
     * Receives cat event data, processes event detection logic,
     * and triggers notification if event is key.
     */
    @PostMapping("/events/detect")
    public ResponseEntity<EventDetectResponse> detectEvent(@RequestBody CatEventRequest catEventRequest) {
        log.info("Received event detection request: {}", catEventRequest);

        try {
            // Basic validation
            if (catEventRequest.getEventType() == null || catEventRequest.getEventType().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType is required");
            }

            // Example business logic: only "dramatic_food_request" triggers notification
            if ("dramatic_food_request".equalsIgnoreCase(catEventRequest.getEventType())) {
                // Fire and forget notification sending
                sendNotificationAsync("Emergency! A cat demands snacks", catEventRequest.getEventType());
                return ResponseEntity.ok(new EventDetectResponse("notification_sent",
                        "Notification has been sent."));
            }

            // For other event types, just log event
            log.info("Event logged but no notification triggered: {}", catEventRequest.getEventType());
            return ResponseEntity.ok(new EventDetectResponse("event_logged", "Event has been logged."));

        } catch (ResponseStatusException ex) {
            log.error("Error processing event detection: {}", ex.getReason(), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error processing event detection", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * GET /api/notifications
     * Retrieve recent notifications sent by the system.
     */
    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getNotifications() {
        log.info("Fetching all notifications");
        List<Notification> list = new ArrayList<>(notifications.values());
        // Sort by timestamp descending
        list.sort(Comparator.comparing(Notification::getTimestamp).reversed());
        return ResponseEntity.ok(list);
    }

    /**
     * POST /api/notifications/manual
     * Manually send a notification.
     */
    @PostMapping("/notifications/manual")
    public ResponseEntity<ManualNotificationResponse> manualNotification(@RequestBody ManualNotificationRequest request) {
        log.info("Manual notification request received: {}", request);

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message cannot be empty");
        }

        sendNotificationAsync(request.getMessage(), "manual_override");

        return ResponseEntity.ok(new ManualNotificationResponse("sent", "Notification sent manually."));
    }

    @Async
    void sendNotificationAsync(String message, String eventType) {
        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Replace with actual notification sending logic (email, SMS, push, etc.)
                log.info("Sending notification: [{}], eventType: {}", message, eventType);

                UUID id = UUID.randomUUID();
                Notification notification = new Notification(id, message, eventType, Instant.now());
                notifications.put(id, notification);

                log.info("Notification stored: {}", notification);

            } catch (Exception ex) {
                log.error("Failed to send notification asynchronously", ex);
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
        log.error("Unhandled exception caught", ex);
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- DTOs and data classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatEventRequest {
        private String eventType;
        private Map<String, Object> eventData;
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
        private UUID id;
        private String message;
        private String eventType;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualNotificationRequest {
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
```