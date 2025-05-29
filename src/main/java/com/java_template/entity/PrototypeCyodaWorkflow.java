package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entity-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Track running alarm job in-memory including scheduledFuture and technicalId
    private final Map<String, AlarmJob> alarmJobMap = new ConcurrentHashMap<>();

    private static final Map<String, Integer> BOIL_LEVEL_TO_DURATION_SECONDS = Map.of(
            "soft", 300,
            "medium", 420,
            "hard", 600
    );

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final EntityService entityService;

    private static final String ENTITY_NAME = "alarm_job";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StartAlarmRequest {
        @NotNull
        @Pattern(regexp = "soft|medium|hard")
        private String boilingLevel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StartAlarmResponse {
        private String status;
        private String boilingLevel;
        private int durationSeconds;
        private Instant startTime;
        private Instant expectedEndTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StopAlarmResponse {
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmStatusResponse {
        private String status;
        private String boilingLevel;
        private long remainingSeconds;
        private Instant expectedEndTime;
    }

    // Internal representation of running alarm job for in-memory tracking
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmJob {
        private String boilingLevel;
        private Instant startTime;
        private Instant expectedEndTime;
        @com.fasterxml.jackson.annotation.JsonIgnore
        private ScheduledFuture<?> scheduledFuture;
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
    }

    // Workflow function invoked asynchronously before persisting alarm_job entity
    // Moves all async scheduling and notification logic here
    public CompletableFuture<ObjectNode> processalarm_job(ObjectNode entity) {
        logger.debug("Workflow processalarm_job invoked with entity: {}", entity);

        String boilingLevel = entity.path("boilingLevel").asText(null);
        String startTimeStr = entity.path("startTime").asText(null);
        String expectedEndTimeStr = entity.path("expectedEndTime").asText(null);

        if (boilingLevel == null || startTimeStr == null || expectedEndTimeStr == null) {
            logger.error("Missing required fields in alarm_job entity for workflow");
            return CompletableFuture.completedFuture(entity);
        }

        Instant startTime;
        Instant expectedEndTime;
        try {
            startTime = Instant.parse(startTimeStr);
            expectedEndTime = Instant.parse(expectedEndTimeStr);
        } catch (Exception e) {
            logger.error("Invalid time format in alarm_job entity: {}", e.getMessage());
            return CompletableFuture.completedFuture(entity);
        }

        long delaySeconds = Instant.now().until(expectedEndTime, ChronoUnit.SECONDS);
        if (delaySeconds < 0) delaySeconds = 0;

        final String currentKey = "current";

        // Cancel any previous scheduled task if present
        AlarmJob oldJob = alarmJobMap.get(currentKey);
        if (oldJob != null && oldJob.getScheduledFuture() != null && !oldJob.getScheduledFuture().isDone()) {
            oldJob.getScheduledFuture().cancel(false);
            logger.info("Cancelled previous scheduled alarm task");
        }

        // Schedule alarm notification task
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                logger.info("Alarm notification triggered for boilingLevel '{}'", boilingLevel);
                triggerAlarmNotification(boilingLevel);
            } catch (Exception ex) {
                logger.error("Exception during alarm notification: {}", ex.getMessage(), ex);
            } finally {
                alarmJobMap.remove(currentKey);
                logger.info("Alarm job cleared from map for boilingLevel '{}'", boilingLevel);
            }
        }, delaySeconds, TimeUnit.SECONDS);

        AlarmJob newJob = new AlarmJob(boilingLevel, startTime, expectedEndTime, future, null);
        alarmJobMap.put(currentKey, newJob);

        // No modification to entity needed, but could add metadata if desired
        return CompletableFuture.completedFuture(entity);
    }

    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public StartAlarmResponse startAlarm(@RequestBody @Valid StartAlarmRequest request) throws Exception {
        String level = request.getBoilingLevel();
        logger.info("Received request to start alarm with boilingLevel={}", level);
        if (!BOIL_LEVEL_TO_DURATION_SECONDS.containsKey(level)) {
            logger.error("Invalid boilingLevel received: {}", level);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid boilingLevel. Must be one of: soft, medium, hard");
        }
        if (alarmJobMap.containsKey("current")) {
            logger.error("Alarm already running. Rejecting start request.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alarm is already running. Stop it before starting a new one.");
        }
        int durationSeconds = BOIL_LEVEL_TO_DURATION_SECONDS.get(level);
        Instant now = Instant.now();
        Instant expectedEnd = now.plus(durationSeconds, ChronoUnit.SECONDS);

        ObjectNode alarmJobNode = objectMapper.createObjectNode();
        alarmJobNode.put("boilingLevel", level);
        alarmJobNode.put("startTime", now.toString());
        alarmJobNode.put("expectedEndTime", expectedEnd.toString());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                alarmJobNode,
                this::processalarm_job
        );

        UUID technicalId = idFuture.get();

        // Store technicalId in in-memory map for stop/status operations
        AlarmJob job = alarmJobMap.get("current");
        if (job != null) {
            job.setTechnicalId(technicalId);
        } else {
            logger.warn("Alarm job missing in map after persistence");
        }

        logger.info("Alarm started for boilingLevel={} for {} seconds with technicalId={}", level, durationSeconds, technicalId);
        return new StartAlarmResponse("started", level, durationSeconds, now, expectedEnd);
    }

    @PostMapping(value = "/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public StopAlarmResponse stopAlarm() throws Exception {
        logger.info("Received request to stop alarm");
        AlarmJob job = alarmJobMap.remove("current");
        if (job == null) {
            logger.warn("No running alarm to stop");
            return new StopAlarmResponse("stopped");
        }

        if (job.getScheduledFuture() != null && !job.getScheduledFuture().isDone()) {
            boolean cancelled = job.getScheduledFuture().cancel(false);
            logger.info("Alarm stopped. Cancelled scheduled task: {}", cancelled);
        }

        UUID technicalId = job.getTechnicalId();
        if (technicalId != null) {
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            deletedFuture.get();
        } else {
            logger.warn("No technicalId found for alarm job during stop");
        }

        return new StopAlarmResponse("stopped");
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public AlarmStatusResponse getAlarmStatus() throws Exception {
        AlarmJob job = alarmJobMap.get("current");
        if (job == null) {
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
            if (items.isEmpty()) {
                logger.info("No running alarm found, returning stopped status");
                return new AlarmStatusResponse("stopped", null, 0, null);
            }
            JsonNode obj = items.get(0);
            AlarmJob retrievedJob = objectMapper.treeToValue(obj, AlarmJob.class);
            if (obj.has("technicalId")) {
                try {
                    retrievedJob.setTechnicalId(UUID.fromString(obj.get("technicalId").asText()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid technicalId format in stored alarm_job: {}", e.getMessage());
                }
            }
            // We do not have scheduledFuture here; the alarm might be stale or finished
            alarmJobMap.put("current", retrievedJob);
            job = retrievedJob;
        }

        Instant now = Instant.now();
        long remaining = ChronoUnit.SECONDS.between(now, job.getExpectedEndTime());
        if (remaining < 0) remaining = 0;

        logger.info("Returning alarm status running for boilingLevel={}, remainingSeconds={}", job.getBoilingLevel(), remaining);
        return new AlarmStatusResponse("running", job.getBoilingLevel(), remaining, job.getExpectedEndTime());
    }

    // Internal fire-and-forget notification method called inside workflow task
    void triggerAlarmNotification(String boilingLevel) {
        logger.info("Alarm triggered for boilingLevel '{}'. Sending notification...", boilingLevel);
        try {
            // Simulate notification delay or real notification implementation
            Thread.sleep(500);
        } catch (InterruptedException e) {
            logger.error("Alarm notification interrupted", e);
            Thread.currentThread().interrupt();
        }
        logger.info("Alarm notification completed for boilingLevel '{}'", boilingLevel);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        return Map.of(
                "error", ex.getReason(),
                "status", ex.getStatusCode().value()
        );
    }
}