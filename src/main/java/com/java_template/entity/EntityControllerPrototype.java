```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/api/alarm")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for alarm job state
    // Only one alarm allowed at a time for this prototype
    private final Map<String, AlarmJob> alarmJobMap = new ConcurrentHashMap<>();

    // Timer durations in seconds
    private static final Map<String, Integer> BOIL_LEVEL_TO_DURATION_SECONDS = Map.of(
            "soft", 300,
            "medium", 420,
            "hard", 600
    );

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StartAlarmRequest {
        private String boilingLevel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StartAlarmResponse {
        private String status;
        private String boilingLevel;
        private int durationSeconds;
        private Instant startTime;
        private Instant expectedEndTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StopAlarmResponse {
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmStatusResponse {
        private String status;               // "running" or "stopped"
        private String boilingLevel;         // "soft", "medium", "hard", or null
        private long remainingSeconds;
        private Instant expectedEndTime;     // nullable if stopped
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmJob {
        private String boilingLevel;
        private Instant startTime;
        private Instant expectedEndTime;
        private ScheduledFuture<?> scheduledFuture;
    }

    /**
     * Starts the alarm timer based on the boiling level.
     * Business logic:
     *  - Validate boiling level
     *  - Schedule a timer task to trigger alarm notification asynchronously
     */
    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public StartAlarmResponse startAlarm(@RequestBody StartAlarmRequest request) {
        String level = request.getBoilingLevel();
        log.info("Received request to start alarm with boilingLevel={}", level);

        if (level == null || !BOIL_LEVEL_TO_DURATION_SECONDS.containsKey(level)) {
            log.error("Invalid boilingLevel received: {}", level);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid boilingLevel. Must be one of: soft, medium, hard");
        }

        // If an alarm is already running, reject request
        if (alarmJobMap.containsKey("current")) {
            log.error("Alarm already running. Rejecting start request.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alarm is already running. Stop it before starting a new one.");
        }

        int durationSeconds = BOIL_LEVEL_TO_DURATION_SECONDS.get(level);
        Instant now = Instant.now();
        Instant expectedEnd = now.plus(durationSeconds, ChronoUnit.SECONDS);

        // Schedule the alarm notification asynchronously
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            triggerAlarmNotification(level);
            // After alarm triggers, clear the alarm job
            alarmJobMap.remove("current");
            log.info("Alarm notification triggered and job cleared for boilingLevel={}", level);
        }, durationSeconds, TimeUnit.SECONDS);

        AlarmJob job = new AlarmJob(level, now, expectedEnd, future);
        alarmJobMap.put("current", job);

        log.info("Alarm started for boilingLevel={} for {} seconds", level, durationSeconds);

        return new StartAlarmResponse("started", level, durationSeconds, now, expectedEnd);
    }

    /**
     * Stops the running alarm and resets timer.
     */
    @PostMapping(value = "/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public StopAlarmResponse stopAlarm() {
        log.info("Received request to stop alarm");

        AlarmJob job = alarmJobMap.remove("current");
        if (job == null) {
            log.warn("No running alarm to stop");
            return new StopAlarmResponse("stopped"); // Already stopped
        }

        // Cancel the scheduled alarm task
        boolean cancelled = job.getScheduledFuture().cancel(false);
        log.info("Alarm stopped. Cancelled scheduled task: {}", cancelled);

        return new StopAlarmResponse("stopped");
    }

    /**
     * Retrieves current alarm status and remaining time.
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public AlarmStatusResponse getAlarmStatus() {
        AlarmJob job = alarmJobMap.get("current");
        if (job == null) {
            log.info("No running alarm found, returning stopped status");
            return new AlarmStatusResponse("stopped", null, 0, null);
        }

        Instant now = Instant.now();
        long remaining = ChronoUnit.SECONDS.between(now, job.getExpectedEndTime());
        if (remaining < 0) remaining = 0;

        log.info("Returning alarm status running for boilingLevel={}, remainingSeconds={}", job.getBoilingLevel(), remaining);

        return new AlarmStatusResponse("running", job.getBoilingLevel(), remaining, job.getExpectedEndTime());
    }

    /**
     * This method mocks alarm notification logic.
     * TODO: Replace with real notification system or event publishing.
     */
    @Async
    void triggerAlarmNotification(String boilingLevel) {
        // Fire-and-forget alarm notification logic
        log.info("Alarm triggered for boilingLevel '{}'. Sending notification...", boilingLevel);

        // TODO: Implement real notification (e.g., push notification, sound, external API call)

        try {
            // Simulate notification delay
            Thread.sleep(500);
        } catch (InterruptedException e) {
            log.error("Alarm notification interrupted", e);
            Thread.currentThread().interrupt();
        }
        log.info("Alarm notification completed for boilingLevel '{}'", boilingLevel);
    }

    /**
     * Basic minimal exception handler for ResponseStatusException
     */
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason());
        return Map.of(
                "error", ex.getReason(),
                "status", ex.getStatusCode().value()
        );
    }

}
```
