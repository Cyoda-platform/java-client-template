package com.java_template.entity;

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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/alarms")
public class EntityControllerPrototype {

    private final Map<String, Alarm> alarmStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int SOFT_BOIL_SECONDS = 300;
    private static final int MEDIUM_BOIL_SECONDS = 420;
    private static final int HARD_BOIL_SECONDS = 600;

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized and ready");
    }

    @PostMapping
    public ResponseEntity<AlarmResponse> setAlarm(@RequestBody @Valid AlarmRequest request) {
        log.info("Received request to set alarm for eggType={}", request.getEggType());
        EggType eggType;
        try {
            eggType = EggType.valueOf(request.getEggType().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid eggType provided: {}", request.getEggType());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid eggType. Allowed values: soft, medium, hard");
        }
        Instant now = Instant.now();
        Instant alarmTime = now.plus(getBoilSeconds(eggType), ChronoUnit.SECONDS);
        String alarmId = UUID.randomUUID().toString();
        Alarm alarm = new Alarm(alarmId, eggType, AlarmStatus.SET, now, alarmTime);
        alarmStore.put(alarmId, alarm);
        CompletableFuture.runAsync(() -> triggerAlarmWorkflow(alarm)); // TODO: replace with real scheduling
        AlarmResponse response = new AlarmResponse(alarmId, eggType.name().toLowerCase(), AlarmStatus.SET.name().toLowerCase(), now, alarmTime);
        log.info("Alarm set with id={}, alarmTime={}", alarmId, alarmTime);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{alarmId}")
    public ResponseEntity<AlarmResponse> getAlarmStatus(@PathVariable String alarmId) {
        log.info("Fetching status for alarmId={}", alarmId);
        Alarm alarm = alarmStore.get(alarmId);
        if (alarm == null) {
            log.error("Alarm with id={} not found", alarmId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alarm not found");
        }
        AlarmResponse response = new AlarmResponse(alarm.getAlarmId(), alarm.getEggType().name().toLowerCase(), alarm.getStatus().name().toLowerCase(), alarm.getSetTime(), alarm.getAlarmTime());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{alarmId}/cancel")
    public ResponseEntity<CancelResponse> cancelAlarm(@PathVariable String alarmId) {
        log.info("Request to cancel alarmId={}", alarmId);
        Alarm alarm = alarmStore.get(alarmId);
        if (alarm == null) {
            log.error("Alarm with id={} not found for cancellation", alarmId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alarm not found");
        }
        if (alarm.getStatus() != AlarmStatus.CANCELLED && alarm.getStatus() != AlarmStatus.COMPLETED) {
            alarm.setStatus(AlarmStatus.CANCELLED);
            log.info("Alarm with id={} cancelled", alarmId);
        }
        return ResponseEntity.ok(new CancelResponse(alarmId, alarm.getStatus().name().toLowerCase()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().value(), ex.getReason()));
    }

    private int getBoilSeconds(EggType eggType) {
        return switch (eggType) {
            case SOFT -> SOFT_BOIL_SECONDS;
            case MEDIUM -> MEDIUM_BOIL_SECONDS;
            case HARD -> HARD_BOIL_SECONDS;
        };
    }

    @Async
    void triggerAlarmWorkflow(Alarm alarm) {
        try {
            long delayMillis = ChronoUnit.MILLIS.between(Instant.now(), alarm.getAlarmTime());
            if (delayMillis > 0) Thread.sleep(delayMillis);
            if (alarm.getStatus() == AlarmStatus.CANCELLED) return;
            alarm.setStatus(AlarmStatus.RINGING);
            log.info("Alarm {} is now ringing", alarm.getAlarmId());
            Thread.sleep(5000);
            if (alarm.getStatus() != AlarmStatus.CANCELLED) {
                alarm.setStatus(AlarmStatus.COMPLETED);
                log.info("Alarm {} completed", alarm.getAlarmId());
            }
        } catch (InterruptedException e) {
            log.error("Alarm workflow interrupted for id={}", alarm.getAlarmId(), e);
            Thread.currentThread().interrupt();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmRequest {
        @NotBlank
        @Pattern(regexp = "soft|medium|hard", flags = Pattern.Flag.CASE_INSENSITIVE, message = "eggType must be 'soft', 'medium', or 'hard'")
        private String eggType;
    }

    @Data
    @AllArgsConstructor
    static class AlarmResponse {
        private String alarmId;
        private String eggType;
        private String status;
        private Instant setTime;
        private Instant alarmTime;
    }

    @Data
    @AllArgsConstructor
    static class CancelResponse {
        private String alarmId;
        private String status;
    }

    @Data
    @AllArgsConstructor
    static class ErrorResponse {
        private int statusCode;
        private String message;
    }

    @Data
    @AllArgsConstructor
    static class Alarm {
        private String alarmId;
        private EggType eggType;
        private AlarmStatus status;
        private Instant setTime;
        private Instant alarmTime;
    }

    enum EggType { SOFT, MEDIUM, HARD }

    enum AlarmStatus { SET, RINGING, CANCELLED, COMPLETED }
}