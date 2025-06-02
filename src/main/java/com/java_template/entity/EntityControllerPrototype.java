package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/alarms")
public class EntityControllerPrototype {

    private final Map<String, EggAlarm> alarmStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Integer> EGG_TYPE_DURATION = Map.of(
            "soft", 4,
            "medium", 7,
            "hard", 10
    );

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmRequest {
        @NotBlank
        @Pattern(regexp = "soft|medium|hard", message = "eggType must be one of soft, medium, hard")
        private String eggType;
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

    @PostMapping
    public ResponseEntity<EggAlarm> createAlarm(@RequestBody @Valid AlarmRequest request) {
        log.info("Received new alarm request for eggType={}", request.getEggType());
        String eggType = request.getEggType().toLowerCase();
        Integer duration = EGG_TYPE_DURATION.get(eggType);
        if (duration == null) {
            log.error("Invalid eggType '{}'", eggType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eggType. Allowed: soft, medium, hard");
        }
        String alarmId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Instant ringAt = createdAt.plusSeconds(duration * 60L);
        EggAlarm alarm = new EggAlarm(alarmId, eggType, duration, AlarmStatus.SCHEDULED, createdAt, ringAt);
        alarmStore.put(alarmId, alarm);
        log.info("Alarm scheduled: id={}, eggType={}, duration={}min", alarmId, eggType, duration);

        CompletableFuture.runAsync(() -> triggerAlarmAfterDelay(alarmId, duration));

        return ResponseEntity.status(HttpStatus.CREATED).body(alarm);
    }

    @GetMapping("/{alarmId}")
    public ResponseEntity<EggAlarm> getAlarmStatus(
            @PathVariable @NotBlank String alarmId
    ) {
        log.info("Fetching alarm status for id={}", alarmId);
        EggAlarm alarm = alarmStore.get(alarmId);
        if (alarm == null) {
            log.error("Alarm not found: id={}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }
        return ResponseEntity.ok(alarm);
    }

    @GetMapping
    public ResponseEntity<List<EggAlarm>> listAlarms() {
        log.info("Listing all alarms, total={}", alarmStore.size());
        return ResponseEntity.ok(new ArrayList<>(alarmStore.values()));
    }

    @Async
    void triggerAlarmAfterDelay(String alarmId, int durationMinutes) {
        try {
            log.info("Alarm [{}] countdown started for {} minutes", alarmId, durationMinutes);
            Thread.sleep(durationMinutes * 60L * 1000L);
            EggAlarm alarm = alarmStore.get(alarmId);
            if (alarm != null && alarm.getStatus() == AlarmStatus.SCHEDULED) {
                alarm.setStatus(AlarmStatus.RINGING);
                alarmStore.put(alarmId, alarm);
                log.info("Alarm [{}] is now RINGING", alarmId);
                Thread.sleep(30 * 1000L);
                alarm.setStatus(AlarmStatus.COMPLETED);
                alarmStore.put(alarmId, alarm);
                log.info("Alarm [{}] is now COMPLETED", alarmId);
            }
        } catch (InterruptedException e) {
            log.error("Alarm [{}] countdown interrupted", alarmId, e);
            Thread.currentThread().interrupt();
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getReason());
        log.error("Handled error: status={}, reason={}", ex.getStatusCode().value(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    private JsonNode callExternalApiMock() {
        try {
            String mockJson = "{\"message\":\"This is a mocked external API response.\"}";
            return objectMapper.readTree(mockJson);
        } catch (Exception e) {
            log.error("Failed to parse mock JSON", e);
            return null;
        }
    }
}