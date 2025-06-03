package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-alarm")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_NAME = "alarm";
    private static final String COMPLETION_SCHEDULE_ENTITY_NAME = "alarmCompletionSchedule";

    // Executor for scheduling delayed tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Egg type durations in seconds
    private static final ConcurrentMap<String, Integer> EGG_TYPE_DURATIONS = new ConcurrentHashMap<>() {{
        put("soft", 4 * 60);
        put("medium", 7 * 60);
        put("hard", 10 * 60);
    }};

    // Prevent concurrent starts: simple in-memory guard to avoid race condition during check
    private final AtomicBoolean alarmStartInProgress = new AtomicBoolean(false);

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @PostMapping("/start")
    public ResponseEntity<AlarmResponse> startAlarm(@RequestBody @Valid AlarmRequest request) {
        logger.info("Received start alarm request: {}", request);
        String eggType = request.getEggType();

        if (!EGG_TYPE_DURATIONS.containsKey(eggType)) {
            logger.error("Invalid eggType: {}", eggType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eggType: " + eggType);
        }

        // Use an ObjectNode for alarm entity with only eggType initially
        ObjectNode alarmNode = JsonNodeFactory.instance.objectNode();
        alarmNode.put("eggType", eggType);

        // Call addItem with workflow function processAlarm
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                alarmNode,
                this::processAlarm
        );

        UUID technicalId;
        try {
            technicalId = idFuture.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while starting alarm");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResponseStatusException) {
                throw (ResponseStatusException) cause;
            }
            logger.error("Error starting alarm", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start alarm");
        } catch (TimeoutException e) {
            logger.error("Timeout starting alarm", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Timeout starting alarm");
        }

        // Prepare response with approximate startTime and duration; exact values set in workflow but unavailable here
        Instant now = Instant.now();
        AlarmResponse response = new AlarmResponse(
                technicalId.toString(),
                eggType,
                EGG_TYPE_DURATIONS.get(eggType),
                now.toString(),
                "running"
        );
        logger.info("Alarm started: {}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * Workflow function applied asynchronously before persisting alarm entity.
     * Validates no running alarm exists, sets duration, startTime, status,
     * creates a supplementary alarmCompletionSchedule entity to schedule completion.
     * Returns modified alarm entity for persistence.
     */
    private ObjectNode processAlarm(ObjectNode alarm) {
        logger.debug("Processing alarm entity before persistence: {}", alarm);

        String eggType = alarm.get("eggType").asText();

        // Prevent concurrent startAlarm workflows from proceeding simultaneously
        if (!alarmStartInProgress.compareAndSet(false, true)) {
            logger.warn("Alarm start already in progress, rejecting new alarm start");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another alarm start is in progress");
        }

        try {
            // Check if any alarm is currently running
            CompletableFuture<ArrayNode> runningAlarmsFuture = entityService.getItemsByCondition(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    "{\"status\":\"running\"}"
            );
            ArrayNode runningAlarms = runningAlarmsFuture.get(5, TimeUnit.SECONDS);

            if (runningAlarms != null && runningAlarms.size() > 0) {
                logger.warn("Cannot start alarm: another alarm is already running");
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An alarm is already running");
            }

            // Set durationSeconds based on eggType
            int durationSeconds = EGG_TYPE_DURATIONS.getOrDefault(eggType, 4 * 60);
            alarm.put("durationSeconds", durationSeconds);

            // Set startTime and status
            Instant startTime = Instant.now();
            alarm.put("startTime", startTime.toString());
            alarm.put("status", "running");

            // Add supplementary entity alarmCompletionSchedule to schedule completion
            ObjectNode scheduleEntity = JsonNodeFactory.instance.objectNode();
            scheduleEntity.put("eggType", eggType);
            scheduleEntity.put("durationSeconds", durationSeconds);
            scheduleEntity.put("startTime", startTime.toString());

            // Add supplementary entity with its own workflow function processAlarmCompletionSchedule
            entityService.addItem(
                    COMPLETION_SCHEDULE_ENTITY_NAME,
                    ENTITY_VERSION,
                    scheduleEntity,
                    this::processAlarmCompletionSchedule
            );

            return alarm;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted during alarm start");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResponseStatusException) {
                throw (ResponseStatusException) cause;
            }
            logger.error("Error during alarm start workflow", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed during alarm start");
        } catch (TimeoutException e) {
            logger.error("Timeout during alarm start workflow", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Timeout during alarm start");
        } finally {
            alarmStartInProgress.set(false);
        }
    }

    /**
     * Workflow function for alarmCompletionSchedule entity.
     * Schedules a delayed task that updates the alarm status to "completed" after durationSeconds.
     * Queries alarms matching eggType and startTime to update.
     * Returns the schedule entity unmodified for persistence.
     */
    private ObjectNode processAlarmCompletionSchedule(ObjectNode schedule) {
        logger.debug("Processing alarmCompletionSchedule entity: {}", schedule);

        String eggType = schedule.get("eggType").asText();
        int durationSeconds = schedule.get("durationSeconds").asInt();
        String startTimeStr = schedule.get("startTime").asText();

        Instant startTime;
        try {
            startTime = Instant.parse(startTimeStr);
        } catch (Exception e) {
            logger.error("Invalid startTime format in alarmCompletionSchedule: {}", startTimeStr, e);
            return schedule; // Persist anyway but do not schedule
        }

        long elapsedSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        long delaySeconds = durationSeconds - elapsedSeconds;
        if (delaySeconds < 0) delaySeconds = 0;

        // Schedule completion task asynchronously
        scheduler.schedule(() -> {
            try {
                // Query running alarms with matching eggType and startTime (may be more than one but should be only one)
                CompletableFuture<ArrayNode> alarmsFuture = entityService.getItemsByCondition(
                        ENTITY_NAME,
                        ENTITY_VERSION,
                        "{\"status\":\"running\",\"eggType\":\"" + eggType + "\",\"startTime\":\"" + startTimeStr + "\"}"
                );
                ArrayNode alarms = alarmsFuture.get(5, TimeUnit.SECONDS);
                if (alarms != null && alarms.size() > 0) {
                    for (JsonNode alarmNode : alarms) {
                        if (!alarmNode.hasNonNull("technicalId")) {
                            logger.warn("Alarm entity missing technicalId, cannot update status");
                            continue;
                        }
                        UUID alarmId;
                        try {
                            alarmId = UUID.fromString(alarmNode.get("technicalId").asText());
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid technicalId format: {}", alarmNode.get("technicalId").asText());
                            continue;
                        }
                        ObjectNode updatedAlarm = (ObjectNode) alarmNode.deepCopy();
                        updatedAlarm.put("status", "completed");

                        entityService.updateItem(
                                ENTITY_NAME,
                                ENTITY_VERSION,
                                alarmId,
                                updatedAlarm
                        ).join();

                        logger.info("Alarm {} marked as completed", alarmId);
                    }
                } else {
                    logger.warn("No running alarm found to complete for eggType {} startTime {}", eggType, startTimeStr);
                }
            } catch (Exception e) {
                logger.error("Failed to complete alarm asynchronously", e);
            }
        }, delaySeconds, TimeUnit.SECONDS);

        return schedule;
    }

    @GetMapping("/{alarmId}/status")
    public ResponseEntity<AlarmStatusResponse> getAlarmStatus(@PathVariable @NotBlank String alarmId) {
        logger.info("Received get alarm status request for alarmId: {}", alarmId);
        UUID uuid;
        try {
            uuid = UUID.fromString(alarmId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for alarmId: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alarmId format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                uuid
        );
        ObjectNode node;
        try {
            node = itemFuture.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while fetching alarm status");
        } catch (ExecutionException e) {
            logger.error("Error fetching alarm status", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch alarm status");
        } catch (TimeoutException e) {
            logger.error("Timeout fetching alarm status", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Timeout fetching alarm status");
        }

        if (node == null || node.isEmpty()) {
            logger.error("Alarm not found: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }

        String status = node.path("status").asText(null);
        String eggType = node.path("eggType").asText(null);
        int durationSeconds = node.path("durationSeconds").asInt(-1);
        String startTimeStr = node.path("startTime").asText(null);

        if (status == null || eggType == null || durationSeconds == -1 || startTimeStr == null) {
            logger.error("Incomplete alarm data for id: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Incomplete alarm data");
        }

        Instant startTime;
        try {
            startTime = Instant.parse(startTimeStr);
        } catch (Exception e) {
            logger.error("Invalid startTime format for alarm {}", alarmId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid alarm startTime");
        }

        long elapsedSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        if (elapsedSeconds < 0) elapsedSeconds = 0;

        AlarmStatusResponse statusResponse = new AlarmStatusResponse(
                alarmId,
                eggType,
                durationSeconds,
                startTimeStr,
                elapsedSeconds,
                status
        );
        logger.info("Returning alarm status: {}", statusResponse);
        return ResponseEntity.ok(statusResponse);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().value(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unhandled error: ", ex);
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
}