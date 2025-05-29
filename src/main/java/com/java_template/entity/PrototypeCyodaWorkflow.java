Got it! To follow the best practice and your requirements, we should move all asynchronous logic (including scheduling the alarm notification and managing side effects) into the workflow function `processalarm_job`. This will:

- Keep controllers thin and focused on HTTP request/response.
- Move all async and entity side-effect logic into the workflow function.
- Use `ObjectNode` for entity mutation inside the workflow function.
- Avoid any add/update/delete calls on the current entity inside the workflow (to prevent recursion).
- Use `entityService` to get/add supplementary data entities of different models if needed.

---

### What we should move to `processalarm_job`?

- Scheduling the alarm notification (`scheduler.schedule(...)`).
- Managing the lifecycle and removing from `alarmJobMap` when alarm finishes.
- Triggering the alarm notification asynchronously.
- Possibly setting fields such as `technicalId` or `scheduledFuture` inside the workflow if needed.

---

### What stays in controller?

- Parsing request.
- Validating inputs.
- Calling `entityService.addItem` with workflow.
- Returning responses.

---

### Implementation details:

- The workflow function signature is:
  ```java
  CompletableFuture<ObjectNode> processalarm_job(ObjectNode entity)
  ```
  where `entity` is the JSON representation of `AlarmJob` (since entityService works with `ObjectNode`).

- Inside `processalarm_job`:
  - Extract fields from `entity`.
  - Schedule the alarm notification task (using `ScheduledExecutorService`).
  - Save whatever supplementary entities if needed.
  - Modify `entity` in-place (like adding/removing fields).
  - Return `CompletableFuture.completedFuture(entity)` or async chain.

- The controller will no longer schedule or manage alarm lifecycle except calling `entityService.addItem`.

---

### Updated full Java code with these changes:

```java
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

    /**
     * Workflow function as required by entityService.addItem.
     * Must be named with prefix 'process' + entity name.
     * This function asynchronously processes the AlarmJob entity before persistence.
     * We move all async tasks here: scheduling, notification, lifecycle management.
     *
     * @param entity The AlarmJob as ObjectNode (JSON).
     * @return CompletableFuture with possibly mutated entity to persist.
     */
    public CompletableFuture<ObjectNode> processalarm_job(ObjectNode entity) {
        logger.debug("Workflow processalarm_job invoked with entity: {}", entity);

        // Extract boilingLevel, startTime, expectedEndTime from JSON
        String boilingLevel = entity.path("boilingLevel").asText(null);
        String startTimeStr = entity.path("startTime").asText(null);
        String expectedEndTimeStr = entity.path("expectedEndTime").asText(null);

        if (boilingLevel == null || startTimeStr == null || expectedEndTimeStr == null) {
            // Missing required fields, we can fail or just proceed with no scheduling
            logger.error("Missing required fields in alarm_job entity for workflow");
            return CompletableFuture.completedFuture(entity);
        }

        Instant startTime = Instant.parse(startTimeStr);
        Instant expectedEndTime = Instant.parse(expectedEndTimeStr);
        long delaySeconds = Instant.now().until(expectedEndTime, ChronoUnit.SECONDS);
        if (delaySeconds < 0) delaySeconds = 0;

        // Unique key for current alarm (only one running)
        final String currentKey = "current";

        // Remove any existing scheduledFuture for current alarm if any (not strictly needed)
        AlarmJob oldJob = alarmJobMap.get(currentKey);
        if (oldJob != null && oldJob.getScheduledFuture() != null) {
            oldJob.getScheduledFuture().cancel(false);
        }

        // We need to schedule alarm notification task
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            logger.info("Alarm notification triggered for boilingLevel '{}'", boilingLevel);
            triggerAlarmNotification(boilingLevel);
            alarmJobMap.remove(currentKey);
            logger.info("Alarm job cleared from map for boilingLevel '{}'", boilingLevel);
        }, delaySeconds, TimeUnit.SECONDS);

        // Store in alarmJobMap for status etc
        AlarmJob newJob = new AlarmJob(
                boilingLevel,
                startTime,
                expectedEndTime,
                future,
                null  // technicalId will be set after persistence by controller
        );
        alarmJobMap.put(currentKey, newJob);

        // We cannot set technicalId here because it is generated on persistence
        // But we can store scheduledFuture in memory map for the running alarm

        // No modification to entity needed here except maybe adding some status or timestamps if you want
        // entity.put("someField", "someValue");

        // Return entity unchanged asynchronously
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

        // Create AlarmJob entity node (ObjectNode)
        ObjectNode alarmJobNode = objectMapper.createObjectNode();
        alarmJobNode.put("boilingLevel", level);
        alarmJobNode.put("startTime", now.toString());
        alarmJobNode.put("expectedEndTime", expectedEnd.toString());

        // Persist AlarmJob with workflow function
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                alarmJobNode,
                this::processalarm_job
        );

        UUID technicalId = idFuture.get();

        // Store technicalId in map for stop/status usage (not persisted in workflow)
        AlarmJob job = alarmJobMap.get("current");
        if (job != null) {
            job.setTechnicalId(technicalId);
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

        if (job.getScheduledFuture() != null) {
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
        }

        return new StopAlarmResponse("stopped");
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public AlarmStatusResponse getAlarmStatus() throws Exception {
        AlarmJob job = alarmJobMap.get("current");
        if (job == null) {
            // Attempt to retrieve any running alarm from entityService
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
            if (items.isEmpty()) {
                logger.info("No running alarm found, returning stopped status");
                return new AlarmStatusResponse("stopped", null, 0, null);
            }
            // Take first item as current alarm
            JsonNode obj = items.get(0);
            AlarmJob retrievedJob = objectMapper.treeToValue(obj, AlarmJob.class);
            if (obj.has("technicalId")) {
                retrievedJob.setTechnicalId(UUID.fromString(obj.get("technicalId").asText()));
            }
            alarmJobMap.put("current", retrievedJob);
            job = retrievedJob;
        }
        Instant now = Instant.now();
        long remaining = ChronoUnit.SECONDS.between(now, job.getExpectedEndTime());
        if (remaining < 0) remaining = 0;
        logger.info("Returning alarm status running for boilingLevel={}, remainingSeconds={}", job.getBoilingLevel(), remaining);
        return new AlarmStatusResponse("running", job.getBoilingLevel(), remaining, job.getExpectedEndTime());
    }

    /**
     * Fire and forget notification method invoked inside workflow async task.
     * It can be sync or async. Here simple sync implementation with log and sleep.
     *
     * @param boilingLevel the boiling level of alarm
     */
    void triggerAlarmNotification(String boilingLevel) {
        logger.info("Alarm triggered for boilingLevel '{}'. Sending notification...", boilingLevel);
        // TODO: Implement real notification (e.g., push notification, sound, external API call)
        try {
            Thread.sleep(500); // simulate notification delay
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
```

---

### Summary of changes:

- The controller no longer schedules or manages lifecycle of alarms.
- Controller just creates the raw entity JSON (`ObjectNode`) and calls `entityService.addItem` with `this::processalarm_job`.
- `processalarm_job`:
  - Parses entity JSON fields.
  - Schedules the alarm notification task.
  - Stores alarm data in memory map to track running alarm.
  - Triggers notification async task.
  - Returns entity unchanged (or mutated if needed).
- `triggerAlarmNotification` is called **only inside** the workflow function.
- Stop and status endpoints remain mostly unchanged but use in-memory map to track current alarm.

---

This fully complies with your requirement:

- All async logic moved to workflow function.
- Workflow function modifies entity JSON only.
- No add/update/delete of same entity inside workflow (only side effect is scheduling and notification).
- Controllers are clean and focused on HTTP layer.

Let me know if you want me to adjust or explain anything!