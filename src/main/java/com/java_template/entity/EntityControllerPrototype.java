package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/alarm")
public class EntityControllerPrototype {

    private final Map<String, Alarm> alarmStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, Integer> EGG_TYPE_DURATIONS = Map.of(
            "soft", 4 * 60,
            "medium", 7 * 60,
            "hard", 10 * 60
    );
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/start") // must be first
    public ResponseEntity<AlarmResponse> startAlarm(@RequestBody @Valid AlarmRequest request) {
        log.info("Received start alarm request: {}", request);
        String eggType = request.getEggType();
        if (!EGG_TYPE_DURATIONS.containsKey(eggType)) {
            log.error("Invalid eggType: {}", eggType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eggType: " + eggType);
        }
        boolean anyRunning = alarmStore.values().stream()
                .anyMatch(alarm -> "running".equals(alarm.getStatus()));
        if (anyRunning) {
            log.warn("Attempt to start a new alarm while another is running");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An alarm is already running");
        }
        int durationSeconds = EGG_TYPE_DURATIONS.get(eggType);
        Instant startTime = Instant.now();
        String alarmId = UUID.randomUUID().toString();
        Alarm alarm = new Alarm(alarmId, eggType, durationSeconds, startTime, "running");
        alarmStore.put(alarmId, alarm);
        ScheduledFuture<?> future = scheduler.schedule(() -> completeAlarm(alarmId), durationSeconds, TimeUnit.SECONDS);
        scheduledTasks.put(alarmId, future);
        AlarmResponse response = new AlarmResponse(alarmId, eggType, durationSeconds, startTime.toString(), "running");
        log.info("Alarm started: {}", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{alarmId}/status") // must be first
    public ResponseEntity<AlarmStatusResponse> getAlarmStatus(@PathVariable @NotBlank String alarmId) {
        log.info("Received get alarm status request for alarmId: {}", alarmId);
        Alarm alarm = alarmStore.get(alarmId);
        if (alarm == null) {
            log.error("Alarm not found: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }
        long elapsedSeconds = Instant.now().getEpochSecond() - alarm.getStartTime().getEpochSecond();
        if (elapsedSeconds < 0) elapsedSeconds = 0;
        AlarmStatusResponse statusResponse = new AlarmStatusResponse(
                alarm.getAlarmId(),
                alarm.getEggType(),
                alarm.getDurationSeconds(),
                alarm.getStartTime().toString(),
                elapsedSeconds,
                alarm.getStatus()
        );
        log.info("Returning alarm status: {}", statusResponse);
        return ResponseEntity.ok(statusResponse);
    }

    @Async
    void completeAlarm(String alarmId) {
        log.info("Completing alarm: {}", alarmId);
        Alarm alarm = alarmStore.get(alarmId);
        if (alarm != null && "running".equals(alarm.getStatus())) {
            alarm.setStatus("completed");
            // TODO: Implement real notification logic (sound, push notification, etc.)
            log.info("Alarm {} completed. Notification should be triggered now.", alarmId);
        }
        scheduledTasks.remove(alarmId);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().value(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal server error"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlarmRequest {
        @NotNull
        @Pattern(regexp = "soft|medium|hard", message = "eggType must be 'soft', 'medium', or 'hard'")
        private String eggType;
    }

    @Data
    @AllArgsConstructor
    public static class AlarmResponse {
        private String alarmId;
        private String eggType;
        private int durationSeconds;
        private String startTime;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class AlarmStatusResponse {
        private String alarmId;
        private String eggType;
        private int durationSeconds;
        private String startTime;
        private long elapsedSeconds;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Alarm {
        private String alarmId;
        private String eggType;
        private int durationSeconds;
        private Instant startTime;
        private String status;
    }
}