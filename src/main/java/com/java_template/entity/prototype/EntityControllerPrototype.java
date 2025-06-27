package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/api")
public class EntityControllerPrototype {

    private final Map<UUID, Notification> notifications = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/events/detect")
    public ResponseEntity<EventDetectResponse> detectEvent(@RequestBody @Valid CatEventRequest catEventRequest) {
        log.info("Received event detection request: {}", catEventRequest);
        try {
            if ("dramatic_food_request".equalsIgnoreCase(catEventRequest.getEventType())) {
                sendNotificationAsync("Emergency! A cat demands snacks", catEventRequest.getEventType());
                return ResponseEntity.ok(new EventDetectResponse("notification_sent", "Notification has been sent."));
            }
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

    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getNotifications() {
        log.info("Fetching all notifications");
        List<Notification> list = new ArrayList<>(notifications.values());
        list.sort(Comparator.comparing(Notification::getTimestamp).reversed());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/notifications/manual")
    public ResponseEntity<ManualNotificationResponse> manualNotification(@RequestBody @Valid ManualNotificationRequest request) {
        log.info("Manual notification request received: {}", request);
        sendNotificationAsync(request.getMessage(), "manual_override");
        return ResponseEntity.ok(new ManualNotificationResponse("sent", "Notification sent manually."));
    }

    @Async
    void sendNotificationAsync(String message, String eventType) {
        CompletableFuture.runAsync(() -> {
            try {
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