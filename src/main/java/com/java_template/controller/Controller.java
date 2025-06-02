package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // Using a fixed thread pool for lifecycle async tasks to prevent resource exhaustion
    private final ExecutorService lifecycleExecutor = Executors.newFixedThreadPool(4);

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<AlarmResponse>> setAlarm(@RequestBody @Valid AlarmRequest request) {
        logger.info("Received request to set alarm for eggType={}", request.getEggType());
        EggType eggType;
        try {
            eggType = EggType.valueOf(request.getEggType().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid eggType provided: {}", request.getEggType());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid eggType. Allowed values: soft, medium, hard");
        }
        Instant now = Instant.now();
        Instant alarmTime = now.plus(getBoilSeconds(eggType), ChronoUnit.SECONDS);

        Alarm alarm = new Alarm(null, eggType, AlarmStatus.SET, now, alarmTime);

        // Pass workflow function processAlarm to addItem
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, alarm.toObjectNode())
                .thenApply(technicalId -> {
                    String alarmId = technicalId.toString();
                    logger.info("Alarm set with id={}, alarmTime={}", alarmId, alarmTime);

                    // Fire-and-forget async lifecycle management (outside workflow function!)
                    triggerAlarmLifecycle(technicalId);

                    return ResponseEntity.ok(new AlarmResponse(alarmId, eggType.name().toLowerCase(),
                            AlarmStatus.SET.name().toLowerCase(), now, alarmTime));
                });
    }

    @GetMapping("/{alarmId}")
    public CompletableFuture<ResponseEntity<AlarmResponse>> getAlarmStatus(@PathVariable String alarmId) {
        logger.info("Fetching status for alarmId={}", alarmId);
        UUID technicalId = parseUUID(alarmId);
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Alarm with id={} not found", alarmId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    Alarm alarm = Alarm.fromObjectNode(objectNode);
                    return ResponseEntity.ok(new AlarmResponse(
                            alarmId,
                            alarm.getEggType().name().toLowerCase(),
                            alarm.getStatus().name().toLowerCase(),
                            alarm.getSetTime(),
                            alarm.getAlarmTime()
                    ));
                });
    }

    @PostMapping("/{alarmId}/cancel")
    public CompletableFuture<ResponseEntity<CancelResponse>> cancelAlarm(@PathVariable String alarmId) {
        logger.info("Request to cancel alarmId={}", alarmId);
        UUID technicalId = parseUUID(alarmId);
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenCompose(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Alarm with id={} not found for cancellation", alarmId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    Alarm alarm = Alarm.fromObjectNode(objectNode);
                    if (alarm.getStatus() != AlarmStatus.CANCELLED && alarm.getStatus() != AlarmStatus.COMPLETED) {
                        alarm.setStatus(AlarmStatus.CANCELLED);
                        ObjectNode updatedNode = alarm.toObjectNode();
                        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, updatedNode)
                                .thenApply(updatedId ->
                                        ResponseEntity.ok(new CancelResponse(alarmId, AlarmStatus.CANCELLED.name().toLowerCase()))
                                );
                    } else {
                        return CompletableFuture.completedFuture(
                                ResponseEntity.ok(new CancelResponse(alarmId, alarm.getStatus().name().toLowerCase()))
                        );
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

    private UUID parseUUID(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
    }

    /**
     * Fire-and-forget async lifecycle management after initial persistence.
     * Runs outside the workflow function to avoid recursion.
     */
    private void triggerAlarmLifecycle(UUID technicalId) {
        lifecycleExecutor.submit(() -> {
            try {
                ObjectNode alarmNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId).join();
                if (alarmNode == null || alarmNode.isEmpty()) {
                    logger.warn("Alarm {} disappeared before lifecycle start", technicalId);
                    return;
                }

                Instant alarmTime = Instant.parse(alarmNode.get("alarmTime").asText());

                long delayUntilAlarmMs = ChronoUnit.MILLIS.between(Instant.now(), alarmTime);
                if (delayUntilAlarmMs > 0) {
                    logger.debug("Alarm {} sleeping {} ms until alarmTime", technicalId, delayUntilAlarmMs);
                    Thread.sleep(delayUntilAlarmMs);
                }

                // Refresh status and check terminal conditions
                alarmNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId).join();
                if (alarmNode == null || alarmNode.isEmpty()) {
                    logger.warn("Alarm {} disappeared during lifecycle", technicalId);
                    return;
                }
                AlarmStatus status = AlarmStatus.valueOf(alarmNode.get("status").asText().toUpperCase());
                if (status == AlarmStatus.CANCELLED || status == AlarmStatus.COMPLETED) {
                    logger.info("Alarm {} is in terminal state {} before ringing, aborting", technicalId, status);
                    return;
                }

                // Update status to RINGING
                alarmNode.put("status", AlarmStatus.RINGING.name());
                entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, alarmNode).join();
                logger.info("Alarm {} status updated to RINGING", technicalId);

                // Wait 5 seconds while ringing
                Thread.sleep(5000);

                // Refresh status again before completion
                alarmNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId).join();
                if (alarmNode == null || alarmNode.isEmpty()) {
                    logger.warn("Alarm {} disappeared during ringing", technicalId);
                    return;
                }
                status = AlarmStatus.valueOf(alarmNode.get("status").asText().toUpperCase());
                if (status == AlarmStatus.CANCELLED) {
                    logger.info("Alarm {} was cancelled during ringing, aborting completion", technicalId);
                    return;
                }

                // Update status to COMPLETED
                alarmNode.put("status", AlarmStatus.COMPLETED.name());
                entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, alarmNode).join();
                logger.info("Alarm {} status updated to COMPLETED", technicalId);

            } catch (InterruptedException e) {
                logger.error("Alarm lifecycle interrupted for id={}", technicalId, e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Unexpected error during alarm lifecycle for id={}", technicalId, e);
            }
        });
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
        private UUID technicalId;
        private EggType eggType;
        private AlarmStatus status;
        private Instant setTime;
        private Instant alarmTime;

        public ObjectNode toObjectNode() {
            var mapper = com.fasterxml.jackson.databind.ObjectMapperHolder.mapper();
            ObjectNode node = mapper.createObjectNode();
            if (technicalId != null) {
                node.put("technicalId", technicalId.toString());
            }
            node.put("eggType", eggType.name());
            node.put("status", status.name());
            node.put("setTime", setTime.toString());
            node.put("alarmTime", alarmTime.toString());
            return node;
        }

        public static Alarm fromObjectNode(ObjectNode node) {
            UUID technicalId = node.has("technicalId") && !node.get("technicalId").isNull()
                    ? UUID.fromString(node.get("technicalId").asText()) : null;
            EggType eggType = EggType.valueOf(node.get("eggType").asText().toUpperCase());
            AlarmStatus status = AlarmStatus.valueOf(node.get("status").asText().toUpperCase());
            Instant setTime = Instant.parse(node.get("setTime").asText());
            Instant alarmTime = Instant.parse(node.get("alarmTime").asText());
            return new Alarm(technicalId, eggType, status, setTime, alarmTime);
        }
    }

    enum EggType { SOFT, MEDIUM, HARD }

    enum AlarmStatus { SET, RINGING, CANCELLED, COMPLETED }
}