package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/events/detect")
    public ResponseEntity<EventDetectResponse> detectEvent(@RequestBody @Valid EventDetectRequest request) {
        log.info("Received event detection request: eventType='{}', timestamp='{}'", request.getEventType(), request.getTimestamp());
        boolean isKeyEvent = "food_request".equalsIgnoreCase(request.getEventType());
        String notificationMessage = null;
        if (isKeyEvent) {
            notificationMessage = "Emergency! A cat demands snacks";
            fireAndForgetSendNotification(notificationMessage, "default_human_recipient@example.com");
        }
        EventDetectResponse response = new EventDetectResponse(isKeyEvent, notificationMessage != null ? notificationMessage : "");
        return ResponseEntity.ok(response);
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
        log.info("Sending notification to recipient='{}' with message='{}'", request.getRecipient(), request.getMessage());
        // TODO: Replace mock sending logic with real external integration
        boolean sendSuccess = true;
        if (sendSuccess) {
            NotificationRecord record = new NotificationRecord(null, request.getMessage(), Instant.now());
            return entityService.addItem("notification", ENTITY_VERSION, record)
                    .thenApply(id -> {
                        // update record's id after save (if needed)
                        record.setId(id);
                        log.info("Notification sent and stored with id={}", id);
                        return ResponseEntity.ok(new NotificationSendResponse("sent", "Notification sent successfully"));
                    });
        } else {
            log.error("Failed to send notification to '{}'", request.getRecipient());
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new NotificationSendResponse("failed", "Failed to send notification")));
        }
    }

    @Async
    void fireAndForgetSendNotification(String message, String recipient) {
        CompletableFuture.runAsync(() -> {
            log.info("Async sending notification: '{}' to '{}'", message, recipient);
            NotificationRecord record = new NotificationRecord(null, message, Instant.now());
            entityService.addItem("notification", ENTITY_VERSION, record)
                    .thenAccept(id -> log.info("Async notification stored with id={}", id))
                    .exceptionally(ex -> {
                        log.error("Failed to store async notification", ex);
                        return null;
                    });
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getStatusCode().toString());
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