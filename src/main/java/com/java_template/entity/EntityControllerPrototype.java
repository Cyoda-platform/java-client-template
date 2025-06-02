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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/alarms")
public class EntityControllerPrototype {

    private final Map<String, EggAlarm> alarmStore = new ConcurrentHashMap<>();  // alarmId -> EggAlarm
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Fixed durations for egg types in minutes
    private static final Map<String, Integer> EGG_TYPE_DURATION = Map.of(
            "soft", 4,
            "medium", 7,
            "hard", 10
    );

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmRequest {
        private String eggType; // soft, medium, hard
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class EggAlarm {
        private String alarmId;
        private String eggType;
        private int durationMinutes;
        private AlarmStatus status;
        private Instant createdAt;
        private Instant ringAt;
    }

    enum AlarmStatus {
        SCHEDULED,
        RINGING,
        COMPLETED
    }

    /**
     * Create a new alarm for a given egg type.
     * Business logic (duration calculation) is done here.
     */
    @PostMapping
    public ResponseEntity<EggAlarm> createAlarm(@RequestBody AlarmRequest request) {
        log.info("Received new alarm request for eggType={}", request.getEggType());

        String eggType = Optional.ofNullable(request.getEggType())
                .map(String::toLowerCase)
                .orElseThrow(() -> {
                    log.error("Egg type missing in request");
                    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "eggType is required");
                });

        Integer duration = EGG_TYPE_DURATION.get(eggType);
        if (duration == null) {
            log.error("Invalid eggType '{}'", eggType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eggType. Allowed: soft, medium, hard");
        }

        // TODO: If needed, add logic to allow only one active alarm per user/session
        String alarmId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Instant ringAt = createdAt.plusSeconds(duration * 60L);

        EggAlarm alarm = new EggAlarm(alarmId, eggType, duration, AlarmStatus.SCHEDULED, createdAt, ringAt);
        alarmStore.put(alarmId, alarm);

        logger.info("Alarm scheduled: id={}, eggType={}, duration={}min", alarmId, eggType, duration);

        // Fire-and-forget alarm countdown simulation
        CompletableFuture.runAsync(() -> triggerAlarmAfterDelay(alarmId, duration));

        return ResponseEntity.status(HttpStatus.CREATED).body(alarm);
    }

    /**
     * Internal fire-and-forget method to update alarm status after duration.
     * TODO: Replace with proper scheduler/event-driven mechanism in full implementation.
     */
    @Async
    void triggerAlarmAfterDelay(String alarmId, int durationMinutes) {
        try {
            logger.info("Alarm [{}] countdown started for {} minutes", alarmId, durationMinutes);
            Thread.sleep(durationMinutes * 60L * 1000L);
            EggAlarm alarm = alarmStore.get(alarmId);
            if (alarm != null && alarm.getStatus() == AlarmStatus.SCHEDULED) {
                alarm.setStatus(AlarmStatus.RINGING);
                alarmStore.put(alarmId, alarm);
                logger.info("Alarm [{}] is now RINGING", alarmId);

                // Simulate alarm ringing for 30 seconds before marking completed
                Thread.sleep(30 * 1000L);
                alarm.setStatus(AlarmStatus.COMPLETED);
                alarmStore.put(alarmId, alarm);
                logger.info("Alarm [{}] is now COMPLETED", alarmId);
            }
        } catch (InterruptedException e) {
            logger.error("Alarm [{}] countdown interrupted", alarmId, e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get alarm status by alarmId
     */
    @GetMapping("/{alarmId}")
    public ResponseEntity<EggAlarm> getAlarmStatus(@PathVariable String alarmId) {
        logger.info("Fetching alarm status for id={}", alarmId);
        EggAlarm alarm = alarmStore.get(alarmId);
        if (alarm == null) {
            logger.error("Alarm not found: id={}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }
        return ResponseEntity.ok(alarm);
    }

    /**
     * List all alarms (for prototype, no user context - returns all)
     */
    @GetMapping
    public ResponseEntity<List<EggAlarm>> listAlarms() {
        logger.info("Listing all alarms, total={}", alarmStore.size());
        return ResponseEntity.ok(new ArrayList<>(alarmStore.values()));
    }

    /**
     * Generic minimal error handler for ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getReason());
        logger.error("Handled error: status={}, reason={}", ex.getStatusCode().value(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    /**
     * Example of a mocked external API call - placeholder only.
     * TODO: Replace with real external API calls if needed.
     */
    private JsonNode callExternalApiMock() {
        try {
            String mockJson = "{\"message\":\"This is a mocked external API response.\"}";
            return objectMapper.readTree(mockJson);
        } catch (Exception e) {
            logger.error("Failed to parse mock JSON", e);
            return null;
        }
    }
}
```
