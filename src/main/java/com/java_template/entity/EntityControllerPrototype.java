```java
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

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/alarms")
public class EntityControllerPrototype {

    private final Map<String, Alarm> alarms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Only one active alarm allowed at a time.
    private volatile String activeAlarmId = null;

    private static final int SOFT_TIME_SECONDS = 240;   // 4 minutes
    private static final int MEDIUM_TIME_SECONDS = 420; // 7 minutes
    private static final int HARD_TIME_SECONDS = 600;   // 10 minutes

    @PostConstruct
    public void init() {
        log.info("Egg Alarm EntityControllerPrototype initialized");
    }

    @PostMapping
    public ResponseEntity<AlarmResponse> setAlarm(@RequestBody AlarmRequest request) {
        log.info("Received setAlarm request: eggType={}", request.getEggType());

        int timeSeconds = switch (request.getEggType()) {
            case "soft" -> SOFT_TIME_SECONDS;
            case "medium" -> MEDIUM_TIME_SECONDS;
            case "hard" -> HARD_TIME_SECONDS;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eggType");
        };

        // Cancel existing alarm if any
        if (activeAlarmId != null) {
            Alarm existing = alarms.get(activeAlarmId);
            if (existing != null && !"CANCELLED".equals(existing.getStatus()) && !"TRIGGERED".equals(existing.getStatus())) {
                log.info("Cancelling existing alarm id={}", activeAlarmId);
                existing.setStatus("CANCELLED");
            }
            activeAlarmId = null;
        }

        String alarmId = UUID.randomUUID().toString();
        Alarm alarm = new Alarm(alarmId, request.getEggType(), timeSeconds, "SET", Instant.now());
        alarms.put(alarmId, alarm);
        activeAlarmId = alarmId;

        // Fire-and-forget alarm countdown (simulate timer)
        CompletableFuture.runAsync(() -> triggerAlarmAfterDelay(alarmId, timeSeconds));

        log.info("Alarm set with id={}, eggType={}, timeSeconds={}", alarmId, request.getEggType(), timeSeconds);

        return ResponseEntity.ok(new AlarmResponse(alarmId, request.getEggType(), timeSeconds, "SET"));
    }

    @GetMapping
    public ResponseEntity<?> getActiveAlarm() {
        if (activeAlarmId == null) {
            log.info("No active alarm found");
            return ResponseEntity.ok().build();
        }

        Alarm alarm = alarms.get(activeAlarmId);
        if (alarm == null) {
            log.warn("Active alarmId={} not found in alarms map", activeAlarmId);
            return ResponseEntity.ok().build();
        }

        log.info("Returning active alarm id={}, status={}", alarm.getAlarmId(), alarm.getStatus());
        return ResponseEntity.ok(new AlarmResponse(alarm.getAlarmId(), alarm.getEggType(), alarm.getSetTimeSeconds(), alarm.getStatus()));
    }

    @PostMapping("/{alarmId}/cancel")
    public ResponseEntity<AlarmResponse> cancelAlarm(@PathVariable String alarmId) {
        log.info("Received cancelAlarm request for id={}", alarmId);
        Alarm alarm = alarms.get(alarmId);
        if (alarm == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }
        if ("CANCELLED".equals(alarm.getStatus())) {
            log.info("Alarm id={} already cancelled", alarmId);
            return ResponseEntity.ok(new AlarmResponse(alarmId, alarm.getEggType(), alarm.getSetTimeSeconds(), "CANCELLED"));
        }
        if ("TRIGGERED".equals(alarm.getStatus())) {
            log.info("Alarm id={} already triggered, cannot cancel", alarmId);
            return ResponseEntity.ok(new AlarmResponse(alarmId, alarm.getEggType(), alarm.getSetTimeSeconds(), "TRIGGERED"));
        }

        alarm.setStatus("CANCELLED");
        if (alarmId.equals(activeAlarmId)) {
            activeAlarmId = null;
        }

        log.info("Alarm id={} cancelled", alarmId);
        return ResponseEntity.ok(new AlarmResponse(alarmId, alarm.getEggType(), alarm.getSetTimeSeconds(), "CANCELLED"));
    }

    /**
     * Simulates alarm countdown and triggers alarm notification after delay.
     * TODO: Replace with real scheduling/event-driven workflow in Cyoda platform.
     */
    @Async
    protected void triggerAlarmAfterDelay(String alarmId, int delaySeconds) {
        try {
            log.info("Alarm id={} countdown started for {} seconds", alarmId, delaySeconds);
            Thread.sleep(delaySeconds * 1000L);
        } catch (InterruptedException e) {
            log.error("Alarm countdown interrupted for id={}", alarmId, e);
            Thread.currentThread().interrupt();
            return;
        }

        Alarm alarm = alarms.get(alarmId);
        if (alarm == null) {
            log.warn("Alarm id={} not found at trigger time", alarmId);
            return;
        }
        if ("CANCELLED".equals(alarm.getStatus())) {
            log.info("Alarm id={} was cancelled before triggering", alarmId);
            return;
        }

        alarm.setStatus("TRIGGERED");
        if (alarmId.equals(activeAlarmId)) {
            activeAlarmId = null;
        }

        // TODO: Implement notification mechanism (e.g., WebSocket, push notification)
        log.info("Alarm triggered! id={}, eggType={}", alarmId, alarm.getEggType());
    }

    // Basic error handler for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().value(), ex.getReason()));
    }

    // --- DTOs ---

    @Data
    public static class AlarmRequest {
        private String eggType;
    }

    @Data
    @AllArgsConstructor
    public static class AlarmResponse {
        private String alarmId;
        private String eggType;
        private int setTimeSeconds;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class Alarm {
        private String alarmId;
        private String eggType;
        private int setTimeSeconds;
        private String status;
        private Instant createdAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String message;
    }
}
```
