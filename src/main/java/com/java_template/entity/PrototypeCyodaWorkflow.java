```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-alarms")
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private static final String ENTITY_NAME = "Alarm";

    private volatile UUID activeAlarmTechnicalId = null;

    private static final int SOFT_TIME_SECONDS = 240;
    private static final int MEDIUM_TIME_SECONDS = 420;
    private static final int HARD_TIME_SECONDS = 600;

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("Cyoda Alarm EntityControllerPrototype initialized");
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<AlarmResponse>> setAlarm(@RequestBody @Valid AlarmRequest request) {
        logger.info("Received setAlarm request: eggType={}", request.getEggType());
        int timeSeconds = switch (request.getEggType()) {
            case "soft" -> SOFT_TIME_SECONDS;
            case "medium" -> MEDIUM_TIME_SECONDS;
            case "hard" -> HARD_TIME_SECONDS;
            default -> throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid eggType");
        };
        if (activeAlarmTechnicalId != null) {
            return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, activeAlarmTechnicalId)
                .thenCompose(existingObj -> {
                    if (existingObj == null) {
                        activeAlarmTechnicalId = null;
                        return CompletableFuture.completedFuture(null);
                    }
                    String status = existingObj.path("status").asText();
                    if (!"CANCELLED".equals(status) && !"TRIGGERED".equals(status)) {
                        ((ObjectNode) existingObj).put("status", "CANCELLED");
                        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, activeAlarmTechnicalId, existingObj)
                                .thenApply(updatedId -> {
                                    activeAlarmTechnicalId = null;
                                    return null;
                                });
                    } else {
                        activeAlarmTechnicalId = null;
                        return CompletableFuture.completedFuture(null);
                    }
                }).thenCompose(ignore -> createAndSetNewAlarm(request, timeSeconds));
        } else {
            return createAndSetNewAlarm(request, timeSeconds);
        }
    }

    /**
     * Workflow function that processes Alarm entity before persistence.
     * Here you can modify entity state or perform other operations asynchronously.
     * This function must be named with prefix 'process' followed by entity name as required.
     */
    private CompletableFuture<Alarm> processAlarm(Alarm alarm) {
        // Example: You can modify alarm status or other fields here if needed.
        // For demonstration, just return the alarm as-is.
        return CompletableFuture.completedFuture(alarm);
    }

    private CompletableFuture<ResponseEntity<AlarmResponse>> createAndSetNewAlarm(AlarmRequest request, int timeSeconds) {
        Alarm alarm = new Alarm(null, request.getEggType(), timeSeconds, "SET", Instant.now());

        // Pass processAlarm function as workflow argument to addItem
        return entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                alarm,
                this::processAlarm)
            .thenApply(technicalId -> {
                alarm.setTechnicalId(technicalId);
                activeAlarmTechnicalId = technicalId;
                CompletableFuture.runAsync(() -> triggerAlarmAfterDelay(technicalId, timeSeconds));
                logger.info("Alarm set with technicalId={}, eggType={}, timeSeconds={}", technicalId, request.getEggType(), timeSeconds);
                return ResponseEntity.ok(new AlarmResponse(technicalId.toString(), request.getEggType(), timeSeconds, "SET"));
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<AlarmResponse>> getActiveAlarm() {
        if (activeAlarmTechnicalId == null) {
            logger.info("No active alarm found");
            return CompletableFuture.completedFuture(ResponseEntity.ok().build());
        }
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, activeAlarmTechnicalId)
                .thenApply(alarmObj -> {
                    if (alarmObj == null) {
                        logger.warn("Active alarm technicalId={} not found", activeAlarmTechnicalId);
                        return ResponseEntity.ok().build();
                    }
                    String status = alarmObj.path("status").asText();
                    String eggType = alarmObj.path("eggType").asText();
                    int setTimeSeconds = alarmObj.path("setTimeSeconds").asInt();
                    UUID technicalId = UUID.fromString(alarmObj.path("technicalId").asText());
                    logger.info("Returning active alarm technicalId={}, status={}", technicalId, status);
                    return ResponseEntity.ok(new AlarmResponse(technicalId.toString(), eggType, setTimeSeconds, status));
                });
    }

    @PostMapping("/{alarmId}/cancel")
    public CompletableFuture<ResponseEntity<AlarmResponse>> cancelAlarm(@PathVariable @NotBlank String alarmId) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(alarmId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid alarmId format");
        }
        logger.info("Received cancelAlarm request for technicalId={}", alarmId);
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenCompose(alarmObj -> {
                    if (alarmObj == null) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    String status = alarmObj.path("status").asText();
                    String eggType = alarmObj.path("eggType").asText();
                    int setTimeSeconds = alarmObj.path("setTimeSeconds").asInt();
                    if ("CANCELLED".equals(status) || "TRIGGERED".equals(status)) {
                        logger.info("Alarm technicalId={} already {}", alarmId, status);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.ok(new AlarmResponse(alarmId, eggType, setTimeSeconds, status)));
                    }
                    ((ObjectNode) alarmObj).put("status", "CANCELLED");
                    return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, alarmObj)
                            .thenApply(updatedId -> {
                                if (technicalId.equals(activeAlarmTechnicalId)) {
                                    activeAlarmTechnicalId = null;
                                }
                                logger.info("Alarm technicalId={} cancelled", alarmId);
                                return ResponseEntity.ok(new AlarmResponse(alarmId, eggType, setTimeSeconds, "CANCELLED"));
                            });
                });
    }

    @Async
    protected void triggerAlarmAfterDelay(UUID technicalId, int delaySeconds) {
        try {
            logger.info("Alarm technicalId={} countdown started for {} seconds", technicalId, delaySeconds);
            Thread.sleep(delaySeconds * 1000L);
        } catch (InterruptedException e) {
            logger.error("Alarm countdown interrupted for technicalId={}", technicalId, e);
            Thread.currentThread().interrupt();
            return;
        }
        entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId).thenAccept(alarmObj -> {
            if (alarmObj == null || "CANCELLED".equals(alarmObj.path("status").asText())) {
                logger.info("Alarm technicalId={} not triggered due to cancellation or missing", technicalId);
                return;
            }
            ((ObjectNode) alarmObj).put("status", "TRIGGERED");
            entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, alarmObj)
                    .thenAccept(updatedId -> {
                        if (technicalId.equals(activeAlarmTechnicalId)) {
                            activeAlarmTechnicalId = null;
                        }
                        logger.info("Alarm triggered! technicalId={}, eggType={}", technicalId, alarmObj.path("eggType").asText());
                    });
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
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
    @NoArgsConstructor
    public static class Alarm {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
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
---

### Explanation

- Added a private method `processAlarm(Alarm alarm)` as the workflow function that matches the naming convention `process{EntityName}` (here `processAlarm`).
- Updated the call to `entityService.addItem` in `createAndSetNewAlarm` to pass the workflow function as the last argument.
- The workflow function asynchronously processes the entity before persistence. Here it just returns the given `Alarm` instance as-is but can be extended to modify the alarm.
- Signature of `processAlarm` returns `CompletableFuture<Alarm>` to support async processing.

This satisfies the new `entityService.addItem` method signature and usage requirements.