Certainly! To comply with the requirement of moving all asynchronous logic and side effects into the workflow function `process{entity_name}`, we need to:

- Move any async tasks related to modifying the entity state or triggering side effects (like scheduling, updating status) inside the workflow function.
- The controller endpoint should just prepare the initial entity and call `addItem` with the workflow function.
- The workflow function will do:
  - Validate no running alarms (if needed, but querying inside workflow is tricky and generally not recommended for heavy logic, but we can do it).
  - Schedule the completion task.
  - Update entity state if needed.
- The workflow function receives and returns `ObjectNode` (JSON tree object), so we need to work with `ObjectNode` instead of POJO inside workflow.
- We cannot update the same entity inside the workflow (no `updateItem` or `addItem` for the same entityModel), so for updating the status on completion, we must create a separate entity or some external scheduling mechanism.
- Because the workflow is asynchronous, we can trigger delayed tasks from inside it, or create supplementary entities (with a different entityModel) that contain scheduled task info.

---

### Approach:

- **At startAlarm endpoint:**
  - Prepare minimal `ObjectNode` with initial alarm data.
  - Call `entityService.addItem` with `workflow=processAlarm`.
- **In `processAlarm(ObjectNode alarm)`:**
  - Check for running alarms (getItemsByCondition).
  - If running alarm exists, throw exception (workflow supports throwing exceptions to fail persistence).
  - Compute duration from eggType.
  - Set start time, status, etc.
  - Schedule completion task using scheduler.
  - The scheduled task will update the alarm status to "completed" asynchronously by calling `updateItem`.
- The scheduled task and alarm completion logic fully move inside `processAlarm`.
- The controller endpoints become much simpler.
- `getAlarmStatus` stays the same (just reads entity).

---

### Updated Code

```java
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-alarm")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_NAME = "alarm";

    // Use a scheduler for delayed tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Egg type durations in seconds
    private static final ConcurrentMap<String, Integer> EGG_TYPE_DURATIONS = new ConcurrentHashMap<>() {{
        put("soft", 4 * 60);
        put("medium", 7 * 60);
        put("hard", 10 * 60);
    }};

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

        // Prepare minimal alarm ObjectNode
        ObjectNode alarmNode = JsonNodeFactory.instance.objectNode();
        alarmNode.put("eggType", eggType);
        // durationSeconds, startTime, status, technicalId will be set by workflow function

        // Add with workflow function that does all logic including scheduling async completion
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                alarmNode,
                this::processAlarm
        );
        UUID technicalId = idFuture.join();

        AlarmResponse response = new AlarmResponse(
                technicalId.toString(),
                eggType,
                EGG_TYPE_DURATIONS.get(eggType),
                Instant.now().toString(), // approximate, workflow sets exact startTime
                "running"
        );
        logger.info("Alarm started: {}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * Workflow function applied asynchronously before persisting alarm entity.
     * This function:
     * - validates no running alarm exists,
     * - sets duration, startTime, status,
     * - schedules completion task asynchronously,
     * - returns modified entity for persistence.
     *
     * @param alarm ObjectNode representing the alarm entity
     * @return modified alarm ObjectNode
     */
    private ObjectNode processAlarm(ObjectNode alarm) {
        logger.debug("Processing alarm entity before persistence: {}", alarm);

        String eggType = alarm.get("eggType").asText();

        // 1) Check if any alarm is running. This ensures no concurrent running alarms.
        try {
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
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Error checking running alarms", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to validate alarm state");
        }

        // 2) Set durationSeconds based on egg type
        int durationSeconds = EGG_TYPE_DURATIONS.getOrDefault(eggType, 4 * 60);
        alarm.put("durationSeconds", durationSeconds);

        // 3) Set startTime and status
        Instant startTime = Instant.now();
        alarm.put("startTime", startTime.toString());
        alarm.put("status", "running");

        // 4) The technicalId is not yet known here, but will be assigned after persistence.
        // So schedule completion after persistence, once ID is assigned.
        // For that, we use a trick:
        // We'll schedule the completion NOT here, but in a separate async thread that waits for persistence result.
        // But since workflow only gets the entity data, we cannot get the UUID here.
        // So instead, schedule completion from outside after addItem returns.
        // But requirement says all async tasks must be moved inside workflow.
        // Since workflow supports async code, but does NOT know the UUID yet (it's generated by persistence),
        // we can move the scheduling after persistence by returning a CompletableFuture that schedules after persistence.

        // However, the addItem method returns CompletableFuture<UUID> to controller.
        // The workflow function only modifies entity before persistence.
        // So we cannot get UUID inside workflow.

        // Therefore, we can schedule completion inside workflow by adding a scheduled task entity of a different model,
        // or by launching a separate thread that waits for the persistence result (not feasible here).

        // Given constraints, the best approach is:
        // - Workflow sets the alarm as running with startTime and duration.
        // - Create a supplementary entity of model "alarmCompletionSchedule" with fields:
        //   - alarmId (to be set after persistence)
        //   - durationSeconds
        //   - startTime
        // - This supplementary entity is added inside workflow, and some background process watches it to update alarm status.
        // BUT it says we cannot add/update/delete the same entityModel, but other entityModels are allowed.
        // So let's create supplementary entity here.

        // For demo, create "alarmCompletionSchedule" entity with startTime and durationSeconds only.
        ObjectNode scheduleEntity = JsonNodeFactory.instance.objectNode();
        scheduleEntity.put("eggType", eggType);
        scheduleEntity.put("durationSeconds", durationSeconds);
        scheduleEntity.put("startTime", startTime.toString());

        // We cannot set alarmId here (unknown yet), so we can use a unique temporary id or null.
        // Or store some temporary data.

        // Add supplementary entity asynchronously - fire and forget.
        entityService.addItem(
                "alarmCompletionSchedule",
                ENTITY_VERSION,
                scheduleEntity,
                this::processAlarmCompletionSchedule
        );

        return alarm;
    }

    /**
     * Workflow function for alarmCompletionSchedule entity.
     * This function schedules the async task that updates the alarm status after durationSeconds.
     * Since this entity does not represent the alarm, it can safely schedule async update of alarm.
     *
     * @param schedule ObjectNode representing the alarmCompletionSchedule entity
     * @return the same schedule entity unmodified for persistence
     */
    private ObjectNode processAlarmCompletionSchedule(ObjectNode schedule) {
        logger.debug("Processing alarmCompletionSchedule entity: {}", schedule);

        String eggType = schedule.get("eggType").asText();
        int durationSeconds = schedule.get("durationSeconds").asInt();
        String startTimeStr = schedule.get("startTime").asText();

        Instant startTime = Instant.parse(startTimeStr);
        long delaySeconds = durationSeconds - (Instant.now().getEpochSecond() - startTime.getEpochSecond());
        if (delaySeconds < 0) delaySeconds = 0;

        // Schedule task to update alarm status after delaySeconds
        scheduler.schedule(() -> {
            try {
                // Find the running alarm with this eggType and startTime, then update it to completed
                CompletableFuture<ArrayNode> alarmsFuture = entityService.getItemsByCondition(
                        ENTITY_NAME,
                        ENTITY_VERSION,
                        "{\"status\":\"running\",\"eggType\":\"" + eggType + "\",\"startTime\":\"" + startTimeStr + "\"}"
                );
                ArrayNode alarms = alarmsFuture.get(5, TimeUnit.SECONDS);
                if (alarms != null && alarms.size() > 0) {
                    for (JsonNode alarmNode : alarms) {
                        UUID alarmId = UUID.fromString(alarmNode.get("technicalId").asText());
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
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                UUID.fromString(alarmId)
        );
        ObjectNode node = itemFuture.join();
        if (node == null || node.isEmpty()) {
            logger.error("Alarm not found: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }

        String status = node.get("status").asText();
        String eggType = node.get("eggType").asText();
        int durationSeconds = node.get("durationSeconds").asInt();
        String startTimeStr = node.get("startTime").asText();
        Instant startTime = Instant.parse(startTimeStr);

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
```

---

### Explanation:

- `startAlarm` endpoint creates minimal alarm entity (just `eggType`).
- Calls `entityService.addItem` with workflow function `processAlarm`.
- `processAlarm`:
  - Checks for running alarms (throws exception if any).
  - Sets `durationSeconds`, `startTime`, `status`.
  - Creates a supplementary entity `alarmCompletionSchedule` with same duration and startTime.
  - Adds it via `entityService.addItem` with workflow function `processAlarmCompletionSchedule`.
- `processAlarmCompletionSchedule` schedules a delayed task to update the alarm status to `completed` after the duration.
  - It queries the alarm by `eggType` and `startTime` and updates its status.
- The scheduled task uses `entityService.updateItem` on the alarm entity (allowed since it's a different async task outside the workflow).
- This fully moves async scheduling and state updates out of the controller and into workflow functions.
- The `getAlarmStatus` endpoint just reads alarm state by ID.

---

This approach respects the constraints on workflow functions and entityService usage:

- No add/update/delete on same entity inside workflow function.
- All async/scheduling is done inside workflow functions or triggered by them.
- Controller is very slim, just calls addItem with workflow.
- Workflow functions handle validation, state setting, and scheduling async completion.

Let me know if you want me to further polish or split the code!