package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/alarms")
public class EntityControllerPrototype {

    private final Map<String, Alarm> alarms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String activeAlarmId = null;

    private static final int SOFT_TIME_SECONDS = 240;
    private static final int MEDIUM_TIME_SECONDS = 420;
    private static final int HARD_TIME_SECONDS = 600;

    @PostConstruct
    public void init() {
        log.info("Egg Alarm EntityControllerPrototype initialized");
    }

    @PostMapping
    public ResponseEntity<AlarmResponse> setAlarm(@RequestBody @Valid AlarmRequest request) {
        log.info("Received setAlarm request: eggType={}", request.getEggType());
        int timeSeconds = switch (request.getEggType()) {
            case "soft" -> SOFT_TIME_SECONDS;
            case "medium" -> MEDIUM_TIME_SECONDS;
            case "hard" -> HARD_TIME_SECONDS;
            default -> throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid eggType");
        };
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
        CompletableFuture.runAsync(() -> triggerAlarmAfterDelay(alarmId, timeSeconds)); // TODO: replace with real workflow
        log.info("Alarm set with id={}, eggType={}, timeSeconds={}", alarmId, request.getEggType(), timeSeconds);
        return ResponseEntity.ok(new AlarmResponse(alarmId, request.getEggType(), timeSeconds, "SET"));
    }

    @GetMapping
    public ResponseEntity<AlarmResponse> getActiveAlarm() {
        if (activeAlarmId == null) {
            log.info("No active alarm found");
            return ResponseEntity.ok().build();
        }
        Alarm alarm = alarms.get(activeAlarmId);
        if (alarm == null) {
            log.warn("Active alarmId={} not found", activeAlarmId);
            return ResponseEntity.ok().build();
        }
        log.info("Returning active alarm id={}, status={}", alarm.getAlarmId(), alarm.getStatus());
        return ResponseEntity.ok(new AlarmResponse(alarm.getAlarmId(), alarm.getEggType(), alarm.getSetTimeSeconds(), alarm.getStatus()));
    }

    @PostMapping("/{alarmId}/cancel")
    public ResponseEntity<AlarmResponse> cancelAlarm(@PathVariable @NotBlank String alarmId) {
        log.info("Received cancelAlarm request for id={}", alarmId);
        Alarm alarm = alarms.get(alarmId);
        if (alarm == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alarm not found");
        }
        if ("CANCELLED".equals(alarm.getStatus()) || "TRIGGERED".equals(alarm.getStatus())) {
            log.info("Alarm id={} already {}", alarmId, alarm.getStatus());
            return ResponseEntity.ok(new AlarmResponse(alarmId, alarm.getEggType(), alarm.getSetTimeSeconds(), alarm.getStatus()));
        }
        alarm.setStatus("CANCELLED");
        if (alarmId.equals(activeAlarmId)) {
            activeAlarmId = null;
        }
        log.info("Alarm id={} cancelled", alarmId);
        return ResponseEntity.ok(new AlarmResponse(alarmId, alarm.getEggType(), alarm.getSetTimeSeconds(), "CANCELLED"));
    }

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
        if (alarm == null || "CANCELLED".equals(alarm.getStatus())) {
            log.info("Alarm id={} not triggered due to cancellation or missing", alarmId);
            return;
        }
        alarm.setStatus("TRIGGERED");
        if (alarmId.equals(activeAlarmId)) {
            activeAlarmId = null;
        }
        log.info("Alarm triggered! id={}, eggType={}", alarmId, alarm.getEggType());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
            .body(new ErrorResponse(ex.getStatusCode().value(), ex.getReason()));
    }

    @Data
    public static class AlarmRequest {
        @NotNull
        @Pattern(regexp = "soft|medium|hard", message = "eggType must be one of soft, medium, or hard")
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