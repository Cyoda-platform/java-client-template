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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private final Map<String, Notification> notifications = new ConcurrentHashMap<>();
    private final Map<String, NotificationPreference> preferences = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PostMapping("/events/detect")
    public ResponseEntity<DetectResponse> detectEvent(@RequestBody @Valid CatEvent event) {
        log.info("Received event detection request: {}", event);
        boolean notificationSent = false;
        String notificationMessage = "";

        if ("dramatic_food_request".equalsIgnoreCase(event.getEventType())) {
            notificationMessage = "Emergency! A cat demands snacks.";
            sendNotificationAsync(event.getCatId(), event.getEventType(), notificationMessage);
            notificationSent = true;
        }

        DetectResponse response = new DetectResponse(notificationSent, notificationMessage);
        log.info("Detection response: {}", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getNotifications() {
        log.info("Fetching all notifications; count={}", notifications.size());
        List<Notification> sorted = new ArrayList<>(notifications.values());
        sorted.sort(Comparator.comparing(Notification::getTimestamp).reversed());
        return ResponseEntity.ok(sorted);
    }

    @PostMapping("/notifications/preferences")
    public ResponseEntity<PreferenceResponse> setPreferences(@RequestBody @Valid NotificationPreference preference) {
        log.info("Updating notification preferences: {}", preference);
        preferences.put(preference.getCatId() + ":" + preference.getNotificationType(), preference);
        PreferenceResponse response = new PreferenceResponse(true, "Preferences updated.");
        log.info("Preferences updated for catId={} type={}", preference.getCatId(), preference.getNotificationType());
        return ResponseEntity.ok(response);
    }

    @Async
    public CompletableFuture<Void> sendNotificationAsync(String catId, String eventType, String message) {
        return CompletableFuture.runAsync(() -> {
            try {
                String id = UUID.randomUUID().toString();
                Instant now = Instant.now();
                log.info("Simulating notification sending for catId={} eventType={}", catId, eventType);
                Notification notification = new Notification(id, catId, eventType, now.toString(), message);
                notifications.put(id, notification);
                log.info("Notification stored: {}", notification);
            } catch (Exception e) {
                log.error("Failed to send notification", e);
            }
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
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
    public static class DetectResponse {
        private boolean notificationSent;
        private String message;
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
    public static class NotificationPreference {
        @NotBlank
        private String catId;
        @NotBlank
        private String notificationType;
        private boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferenceResponse {
        private boolean success;
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