package com.java_template.entity.prototype;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("prototype")
public class EntityControllerPrototype {

    private final Map<String, Notification> notifications = new ConcurrentHashMap<>();

    @PostMapping("/events/detect")
    public ResponseEntity<SuccessResponse> detectEvent(@RequestBody @Valid CatEvent event) {
        log.info("Received event detection request: {}", event);

        if (event.getCatId() == null || event.getCatId().isBlank()) {
            return ResponseEntity.badRequest().body(new SuccessResponse(false, "catId is required"));
        }

        if (event.getEventType() == null || event.getEventType().isBlank()) {
            return ResponseEntity.badRequest().body(new SuccessResponse(false, "eventType is required"));
        }

        if (event.getEventTimestamp() == null || event.getEventTimestamp().isBlank()) {
            return ResponseEntity.badRequest().body(new SuccessResponse(false, "eventTimestamp is required"));
        }

        // For prototype, assume notification is always sent if event received
        sendNotificationAsync(event.getCatId(), event.getEventType());

        return ResponseEntity.ok(new SuccessResponse(true, "Event received and notification sent"));
    }

    @Async
    public CompletableFuture<Void> sendNotificationAsync(String catId, String eventType) {
        return CompletableFuture.runAsync(() -> {
            try {
                String id = UUID.randomUUID().toString();
                Instant now = Instant.now();
                String message = "Emergency! A cat demands snacks.";

                Notification notification = new Notification(id, catId, eventType, now.toString(), message);
                notifications.put(id, notification);

                log.info("Notification stored: {}", notification);
            } catch (Exception e) {
                log.error("Failed to send notification", e);
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
