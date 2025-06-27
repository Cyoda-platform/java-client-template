package com.java_template.entity.prototype;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("prototype/cat_event")
public class EntityControllerPrototype {

    private final Map<String, CatEvent> eventStore = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatEventDetectRequest {
        @NotBlank
        @Size(min = 3, max = 50)
        private String eventType;

        @NotBlank
        @Size(max = 200)
        private String eventDescription;

        @NotBlank
        private String timestamp; // ISO8601
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatEventDetectResponse {
        private String status;
        private String message;
        private String eventId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatEvent {
        private String eventId;
        private String eventType;
        private String eventDescription;
        private Instant timestamp;
        private String notificationStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ErrorResponse {
        private String error;
        private String message;
    }

    @PostMapping("/detect")
    public ResponseEntity<CatEventDetectResponse> detectCatEvent(@RequestBody @Valid CatEventDetectRequest request) {
        log.info("Received detection request: {}", request);
        Instant eventInstant;
        try {
            eventInstant = Instant.parse(request.getTimestamp());
        } catch (Exception e) {
            log.error("Invalid timestamp: {}", request.getTimestamp(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timestamp must be ISO8601");
        }

        String eventId = UUID.randomUUID().toString();
        CatEvent event = new CatEvent(eventId,
                                      request.getEventType(),
                                      request.getEventDescription(),
                                      eventInstant,
                                      "pending");
        eventStore.put(eventId, event);
        CompletableFuture.runAsync(() -> processEventAndNotify(event))
                         .exceptionally(ex -> { log.error("Processing error for {}: {}", eventId, ex.getMessage(), ex);
                                               event.setNotificationStatus("failed");
                                               eventStore.put(eventId, event);
                                               return null; });

        return ResponseEntity.ok(new CatEventDetectResponse("success", "Notification sent", eventId));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<CatEvent> getCatEvent(@PathVariable @NotBlank String eventId) {
        CatEvent event = eventStore.get(eventId);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cat event not found with id " + eventId);
        }
        return ResponseEntity.ok(event);
    }

    @GetMapping
    public ResponseEntity<List<CatEvent>> listCatEvents(
            @RequestParam(required = false) @Size(min = 3, max = 50) String eventType,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        List<CatEvent> filtered = new ArrayList<>();
        for (CatEvent e : eventStore.values()) {
            if (eventType == null || eventType.equals(e.getEventType())) {
                filtered.add(e);
            }
        }
        filtered.sort(Comparator.comparing(CatEvent::getTimestamp).reversed());
        if (filtered.size() > limit) {
            filtered = filtered.subList(0, limit);
        }
        return ResponseEntity.ok(filtered);
    }

    @Async
    void processEventAndNotify(CatEvent event) {
        try {
            Thread.sleep(500);
            sendNotification(event);
            event.setNotificationStatus("sent");
            eventStore.put(event.getEventId(), event);
        } catch (InterruptedException e) {
            log.error("Interrupted processing {}", event.getEventId(), e);
            Thread.currentThread().interrupt();
            event.setNotificationStatus("failed");
            eventStore.put(event.getEventId(), event);
        }
    }

    void sendNotification(CatEvent event) {
        String msg = String.format("Emergency! A cat demands snacks (Type: %s, Description: %s)",
                                    event.getEventType(), event.getEventDescription());
        log.info("Sending notification: {}", msg);
        // TODO: integrate real notification channel
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleException(ResponseStatusException ex) {
        ErrorResponse err = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }
}