```java
package com.java_template.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api/alarm")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<String, Alarm> alarms = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CreateAlarmRequest {
        @NotNull
        @Pattern(regexp = "soft-boiled|medium-boiled|hard-boiled", message = "Invalid egg type")
        private String eggType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Alarm {
        private String eggType;
        private String status;
        private LocalDateTime requestedAt;
        private int time; // time in seconds
    }

    @PostMapping
    public ResponseEntity<Alarm> createAlarm(@RequestBody @Valid CreateAlarmRequest request) {
        String eggType = request.getEggType();
        int time;

        switch (eggType) {
            case "soft-boiled":
                time = 300;
                break;
            case "medium-boiled":
                time = 420;
                break;
            case "hard-boiled":
                time = 600;
                break;
            default:
                logger.error("Invalid egg type: {}", eggType);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid egg type");
        }

        String alarmId = UUID.randomUUID().toString();
        Alarm alarm = new Alarm(eggType, "active", LocalDateTime.now(), time);
        alarms.put(alarmId, alarm);

        logger.info("Alarm created: {}", alarmId);

        return ResponseEntity.status(HttpStatus.CREATED).body(alarm);
    }

    @GetMapping("/status")
    public ResponseEntity<Alarm> getAlarmStatus(@RequestParam @NotBlank String alarmId) {
        Alarm alarm = alarms.get(alarmId);
        if (alarm == null) {
            logger.error("Alarm not found: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }

        logger.info("Alarm status retrieved: {}", alarmId);
        return ResponseEntity.ok(alarm);
    }

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancelAlarm(@RequestParam @NotBlank String alarmId) {
        Alarm alarm = alarms.remove(alarmId);
        if (alarm == null) {
            logger.error("Alarm not found: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }

        logger.info("Alarm cancelled: {}", alarmId);
        return ResponseEntity.ok(Map.of("message", "Alarm cancelled"));
    }

    // TODO: Add @Async or fire-and-forget logic for alarm countdown and notification
}
```