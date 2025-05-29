Certainly! Below is the updated complete Java code for `CyodaEntityControllerPrototype.java` where:

- A workflow function `processAlarm_job` is added and implemented.
- The `entityService.addItem` call is updated to take the workflow function as an additional argument.
- The workflow function asynchronously processes the entity before persistence. Here, it simply returns the entity unchanged, but you can modify it as needed.

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;

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
     * @param alarmJob the entity to process
     * @return processed entity (can be modified)
     */
    public CompletableFuture<AlarmJob> processalarm_job(AlarmJob alarmJob) {
        // Example workflow: You can modify alarmJob here if needed.
        logger.debug("Processing alarm_job entity before persistence: {}", alarmJob);
        // For now, just return the entity unchanged asynchronously.
        return CompletableFuture.completedFuture(alarmJob);
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
        AlarmJob job = new AlarmJob(level, now, expectedEnd, null, null);

        // Persist the AlarmJob via entityService, passing the workflow function
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                job,
                this::processalarm_job
        );
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            triggerAlarmNotification(level);
            alarmJobMap.remove("current");
            logger.info("Alarm notification triggered and job cleared for boilingLevel={}", level);
        }, durationSeconds, TimeUnit.SECONDS);

        job.setScheduledFuture(future);
        alarmJobMap.put("current", job);

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
        UUID technicalId = job.getTechnicalId();
        if (job.getScheduledFuture() != null) {
            boolean cancelled = job.getScheduledFuture().cancel(false);
            logger.info("Alarm stopped. Cancelled scheduled task: {}", cancelled);
        }

        // Delete from entityService
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
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
            ArrayNode items = itemsFuture.get();
            if (items.isEmpty()) {
                logger.info("No running alarm found, returning stopped status");
                return new AlarmStatusResponse("stopped", null, 0, null);
            }
            // Take first item as current alarm
            ObjectNode obj = (ObjectNode) items.get(0);
            // Map ObjectNode to AlarmJob
            AlarmJob retrievedJob = objectMapper.treeToValue(obj, AlarmJob.class);
            retrievedJob.setTechnicalId(UUID.fromString(obj.get("technicalId").asText()));
            job = retrievedJob;
            alarmJobMap.put("current", job);
        }
        Instant now = Instant.now();
        long remaining = ChronoUnit.SECONDS.between(now, job.getExpectedEndTime());
        if (remaining < 0) remaining = 0;
        logger.info("Returning alarm status running for boilingLevel={}, remainingSeconds={}", job.getBoilingLevel(), remaining);
        return new AlarmStatusResponse("running", job.getBoilingLevel(), remaining, job.getExpectedEndTime());
    }

    @Async
    void triggerAlarmNotification(String boilingLevel) {
        logger.info("Alarm triggered for boilingLevel '{}'. Sending notification...", boilingLevel);
        // TODO: Implement real notification (e.g., push notification, sound, external API call)
        try {
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
```

---

### Explanation of changes:

- Added a new public method `processalarm_job` (note lowercase `alarm_job` to match entity name exactly after `process` prefix):
  - It takes an `AlarmJob` as input.
  - Returns a `CompletableFuture<AlarmJob>`.
  - Currently it just returns the entity unchanged asynchronously.
  - You can modify this function to mutate the entity or call other entityService methods (except on the same entity model).
  
- Modified the call to `entityService.addItem` inside `startAlarm` to pass the workflow function:
  ```java
  CompletableFuture<UUID> idFuture = entityService.addItem(
          ENTITY_NAME,
          ENTITY_VERSION,
          job,
          this::processalarm_job
  );
  ```

This meets the requirement that the workflow function has name starting with `process` followed by the entity name, takes the entity as input, returns the entity asynchronously, and is passed as the last argument to `addItem`.

If you want the workflow function name to match casing exactly with `ENTITY_NAME` (which is `"alarm_job"`), it should be `processalarm_job` as above.

---

If you want me to adjust for any naming conventions or do further enhancements, just ask!