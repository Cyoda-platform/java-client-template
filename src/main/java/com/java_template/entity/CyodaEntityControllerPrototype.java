```java
package com.java_template.entity;

import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/prototype/api/alarm")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;

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

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<Alarm>> createAlarm(@RequestBody @Valid CreateAlarmRequest request) {
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

        Alarm alarm = new Alarm(eggType, "active", LocalDateTime.now(), time);

        return entityService.addItem("Alarm", ENTITY_VERSION, alarm)
                .thenApply(technicalId -> {
                    logger.info("Alarm created: {}", technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(alarm);
                });
    }

    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<Alarm>> getAlarmStatus(@RequestParam @NotBlank String alarmId) {
        return entityService.getItem("Alarm", ENTITY_VERSION, UUID.fromString(alarmId))
                .thenApply(alarmNode -> {
                    if (alarmNode == null) {
                        logger.error("Alarm not found: {}", alarmId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    Alarm alarm = new Alarm(alarmNode.get("eggType").asText(),
                            alarmNode.get("status").asText(),
                            LocalDateTime.parse(alarmNode.get("requestedAt").asText()),
                            alarmNode.get("time").asInt());
                    logger.info("Alarm status retrieved: {}", alarmId);
                    return ResponseEntity.ok(alarm);
                });
    }

    @PostMapping("/cancel")
    public CompletableFuture<ResponseEntity<Map<String, String>>> cancelAlarm(@RequestParam @NotBlank String alarmId) {
        return entityService.deleteItem("Alarm", ENTITY_VERSION, UUID.fromString(alarmId))
                .thenApply(deletedId -> {
                    if (deletedId == null) {
                        logger.error("Alarm not found: {}", alarmId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    logger.info("Alarm cancelled: {}", alarmId);
                    return ResponseEntity.ok(Map.of("message", "Alarm cancelled"));
                });
    }

    // TODO: Add @Async or fire-and-forget logic for alarm countdown and notification
}
```