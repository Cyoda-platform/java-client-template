package com.java_template.entity;

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
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private final Map<UUID, NotificationRecord> notificationStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * POST /prototype/events/detect
     * Receive cat event data and analyze if it matches key event criteria.
     */
    @PostMapping("/events/detect")
    public ResponseEntity<EventDetectResponse> detectEvent(@RequestBody EventDetectRequest request) {
        log.info("Received event detection request: eventType='{}', timestamp='{}'", request.getEventType(), request.getTimestamp());

        // Simple mock logic: detect only "food_request" as key event
        boolean isKeyEvent = "food_request".equalsIgnoreCase(request.getEventType());
        String notificationMessage = null;

        if (isKeyEvent) {
            notificationMessage = "Emergency! A cat demands snacks";
            // Fire-and-forget sending notification to all humans (mock)
            fireAndForgetSendNotification(notificationMessage, "default_human_recipient@example.com");
        }

        EventDetectResponse response = new EventDetectResponse(isKeyEvent, notificationMessage != null ? notificationMessage : "");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /prototype/notifications
     * Return all stored notifications.
     */
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationRecord>> getNotifications() {
        log.info("Retrieving all notifications, count={}", notificationStore.size());
        List<NotificationRecord> notifications = new ArrayList<>(notificationStore.values());
        notifications.sort(Comparator.comparing(NotificationRecord::getTimestamp).reversed());
        return ResponseEntity.ok(notifications);
    }

    /**
     * POST /prototype/notifications/send
     * Trigger sending a notification (mocked).
     */
    @PostMapping("/notifications/send")
    public ResponseEntity<NotificationSendResponse> sendNotification(@RequestBody NotificationSendRequest request) {
        log.info("Sending notification to recipient='{}' with message='{}'", request.getRecipient(), request.getMessage());

        // TODO: Replace mock sending logic with real external integration (email, SMS, push, etc.)
        boolean sendSuccess = true; // Simulated success

        if (sendSuccess) {
            UUID id = UUID.randomUUID();
            NotificationRecord record = new NotificationRecord(id, request.getMessage(), Instant.now());
            notificationStore.put(id, record);
            log.info("Notification sent and stored with id={}", id);
            NotificationSendResponse response = new NotificationSendResponse("sent", "Notification sent successfully");
            return ResponseEntity.ok(response);
        } else {
            log.error("Failed to send notification to '{}'", request.getRecipient());
            NotificationSendResponse response = new NotificationSendResponse("failed", "Failed to send notification");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Async
    void fireAndForgetSendNotification(String message, String recipient) {
        CompletableFuture.runAsync(() -> {
            log.info("Async sending notification: '{}' to '{}'", message, recipient);
            // TODO: Replace with real sending logic
            UUID id = UUID.randomUUID();
            NotificationRecord record = new NotificationRecord(id, message, Instant.now());
            notificationStore.put(id, record);
            log.info("Async notification stored with id={}", id);
        });
    }

    // --- Exception Handling ---

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

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDetectRequest {
        private String eventType;
        private String eventData;
        private String timestamp; // ISO8601 string
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
        private String message;
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