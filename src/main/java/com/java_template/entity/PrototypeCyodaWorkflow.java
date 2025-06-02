```java
package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-alarms")
public class CyodaEntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private static final String ENTITY_NAME = "alarm";

    private static final int SOFT_BOIL_SECONDS = 300;
    private static final int MEDIUM_BOIL_SECONDS = 420;
    private static final int HARD_BOIL_SECONDS = 600;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized and ready");
    }

    /**
     * Workflow function applied to the entity asynchronously before persistence.
     * It takes the Alarm entity as input, can modify it, and returns it.
     */
    private CompletableFuture<Alarm> processAlarm(Alarm alarm) {
        // Example: you could add some logging or modify the alarm state here before saving.
        logger.debug("Processing alarm entity in workflow before persistence: {}", alarm);
        // For this example, we just return it as is asynchronously.
        return CompletableFuture.completedFuture(alarm);
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<AlarmResponse>> setAlarm(@RequestBody @Valid AlarmRequest request) {
        logger.info("Received request to set alarm for eggType={}", request.getEggType());
        EggType eggType;
        try {
            eggType = EggType.valueOf(request.getEggType().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid eggType provided: {}", request.getEggType());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid eggType. Allowed values: soft, medium, hard");
        }
        Instant now = Instant.now();
        Instant alarmTime = now.plus(getBoilSeconds(eggType), ChronoUnit.SECONDS);

        Alarm alarm = new Alarm(null, eggType, AlarmStatus.SET, now, alarmTime);
        // Use entityService to add item with the workflow function processAlarm
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, alarm, this::processAlarm)
                .thenApply(technicalId -> {
                    String alarmId = technicalId.toString();
                    alarm.setTechnicalId(technicalId);
                    triggerAlarmWorkflow(alarm);
                    AlarmResponse response = new AlarmResponse(alarmId, eggType.name().toLowerCase(), AlarmStatus.SET.name().toLowerCase(), now, alarmTime);
                    logger.info("Alarm set with id={}, alarmTime={}", alarmId, alarmTime);
                    return ResponseEntity.ok(response);
                });
    }

    @GetMapping("/{alarmId}")
    public CompletableFuture<ResponseEntity<AlarmResponse>> getAlarmStatus(@PathVariable String alarmId) {
        logger.info("Fetching status for alarmId={}", alarmId);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(alarmId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid alarmId format");
        }
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Alarm with id={} not found", alarmId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    Alarm alarm = toAlarm(objectNode);
                    AlarmResponse response = new AlarmResponse(
                            alarmId,
                            alarm.getEggType().name().toLowerCase(),
                            alarm.getStatus().name().toLowerCase(),
                            alarm.getSetTime(),
                            alarm.getAlarmTime()
                    );
                    return ResponseEntity.ok(response);
                });
    }

    @PostMapping("/{alarmId}/cancel")
    public CompletableFuture<ResponseEntity<CancelResponse>> cancelAlarm(@PathVariable String alarmId) {
        logger.info("Request to cancel alarmId={}", alarmId);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(alarmId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid alarmId format");
        }
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenCompose(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Alarm with id={} not found for cancellation", alarmId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    Alarm alarm = toAlarm(objectNode);
                    if (alarm.getStatus() != AlarmStatus.CANCELLED && alarm.getStatus() != AlarmStatus.COMPLETED) {
                        alarm.setStatus(AlarmStatus.CANCELLED);
                        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, alarm)
                                .thenApply(updatedId -> {
                                    logger.info("Alarm with id={} cancelled", alarmId);
                                    return ResponseEntity.ok(new CancelResponse(alarmId, AlarmStatus.CANCELLED.name().toLowerCase()));
                                });
                    } else {
                        return CompletableFuture.completedFuture(ResponseEntity.ok(new CancelResponse(alarmId, alarm.getStatus().name().toLowerCase())));
                    }
                });
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
            entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, alarm.getTechnicalId(), alarm).join();
            logger.info("Alarm {} is now ringing", alarm.getTechnicalId());
            Thread.sleep(5000);
            if (alarm.getStatus() != AlarmStatus.CANCELLED) {
                alarm.setStatus(AlarmStatus.COMPLETED);
                entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, alarm.getTechnicalId(), alarm).join();
                logger.info("Alarm {} completed", alarm.getTechnicalId());
            }
        } catch (InterruptedException e) {
            logger.error("Alarm workflow interrupted for id={}", alarm.getTechnicalId(), e);
            Thread.currentThread().interrupt();
        }
    }

    private Alarm toAlarm(ObjectNode node) {
        UUID technicalId = UUID.fromString(node.get("technicalId").asText());
        String eggTypeStr = node.get("eggType").asText();
        EggType eggType = EggType.valueOf(eggTypeStr.toUpperCase());
        String statusStr = node.get("status").asText();
        AlarmStatus status = AlarmStatus.valueOf(statusStr.toUpperCase());
        Instant setTime = Instant.parse(node.get("setTime").asText());
        Instant alarmTime = Instant.parse(node.get("alarmTime").asText());
        return new Alarm(technicalId, eggType, status, setTime, alarmTime);
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
    static class Alarm {
        @JsonIgnore
        private UUID technicalId;
        private EggType eggType;
        private AlarmStatus status;
        private Instant setTime;
        private Instant alarmTime;
    }

    enum EggType { SOFT, MEDIUM, HARD }

    enum AlarmStatus { SET, RINGING, CANCELLED, COMPLETED }
}
```
---

### Explanation of changes:

- Added a private workflow method with the required naming convention:
  ```java
  private CompletableFuture<Alarm> processAlarm(Alarm alarm)
  ```
  This method takes the entity, can modify its state asynchronously, and returns it.

- Updated the call to `entityService.addItem` to include the workflow function as the fourth argument:
  ```java
  return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, alarm, this::processAlarm)
  ```

- The rest of the code remains unchanged, only adapted to the new signature of `addItem`.

This satisfies your requirement that the workflow function is passed to `addItem` and is named with prefix `process` followed by the entity name (`Alarm` in this case).