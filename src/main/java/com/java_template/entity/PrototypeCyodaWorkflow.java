Certainly! Below is the updated complete Java code for your `CyodaEntityControllerPrototype` class, with the required workflow function added to the `entityService.addItem` call. The workflow function is named `processEggAlarm` as per your instructions, and it asynchronously applies any desired modifications to the entity before persistence.

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-alarms")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Integer> EGG_TYPE_DURATION = Map.of(
            "soft", 4,
            "medium", 7,
            "hard", 10
    );

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmRequest {
        @NotBlank
        @Pattern(regexp = "soft|medium|hard", message = "eggType must be one of soft, medium, hard")
        private String eggType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class EggAlarm {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String alarmId;
        private String eggType;
        private int durationMinutes;
        private AlarmStatus status;
        private Instant createdAt;
        private Instant ringAt;
    }

    enum AlarmStatus {
        SCHEDULED,
        RINGING,
        COMPLETED
    }

    // Workflow function to process EggAlarm entity before persistence
    private CompletableFuture<EggAlarm> processEggAlarm(EggAlarm alarm) {
        // Example: You can modify the alarm entity here asynchronously before saving.
        // For instance, add a log or modify state if needed.
        logger.debug("Processing EggAlarm entity in workflow before persistence: alarmId={}", alarm.getAlarmId());
        // Return the alarm as-is wrapped in CompletableFuture
        return CompletableFuture.completedFuture(alarm);
    }

    @PostMapping
    public ResponseEntity<EggAlarm> createAlarm(@RequestBody @Valid AlarmRequest request) {
        logger.info("Received new alarm request for eggType={}", request.getEggType());
        String eggType = request.getEggType().toLowerCase();
        Integer duration = EGG_TYPE_DURATION.get(eggType);
        if (duration == null) {
            logger.error("Invalid eggType '{}'", eggType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eggType. Allowed: soft, medium, hard");
        }
        String alarmId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Instant ringAt = createdAt.plusSeconds(duration * 60L);
        EggAlarm alarm = new EggAlarm(null, alarmId, eggType, duration, AlarmStatus.SCHEDULED, createdAt, ringAt);

        // Pass the workflow function processEggAlarm as the workflow parameter
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "EggAlarm",
                ENTITY_VERSION,
                alarm,
                this::processEggAlarm
        );
        UUID technicalId = idFuture.join();
        alarm.setTechnicalId(technicalId);

        logger.info("Alarm scheduled: technicalId={}, alarmId={}, eggType={}, duration={}min", technicalId, alarmId, eggType, duration);

        CompletableFuture.runAsync(() -> triggerAlarmAfterDelay(technicalId, duration));

        return ResponseEntity.status(HttpStatus.CREATED).body(alarm);
    }

    @GetMapping("/{alarmId}")
    public ResponseEntity<EggAlarm> getAlarmStatus(@PathVariable @NotBlank String alarmId) {
        logger.info("Fetching alarm status for alarmId={}", alarmId);

        String condition = String.format("alarmId = '%s'", alarmId);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                "EggAlarm",
                ENTITY_VERSION,
                condition
        );
        ArrayNode items = filteredItemsFuture.join();

        if (items == null || items.isEmpty()) {
            logger.error("Alarm not found: alarmId={}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }
        ObjectNode node = (ObjectNode) items.get(0);
        EggAlarm alarm = objectMapper.convertValue(node, EggAlarm.class);

        return ResponseEntity.ok(alarm);
    }

    @GetMapping
    public ResponseEntity<List<EggAlarm>> listAlarms() {
        logger.info("Listing all alarms");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                "EggAlarm",
                ENTITY_VERSION
        );
        ArrayNode items = itemsFuture.join();
        List<EggAlarm> alarms = new ArrayList<>();
        if (items != null) {
            for (JsonNode node : items) {
                EggAlarm alarm = objectMapper.convertValue(node, EggAlarm.class);
                alarms.add(alarm);
            }
        }
        logger.info("Total alarms fetched: {}", alarms.size());
        return ResponseEntity.ok(alarms);
    }

    @Async
    void triggerAlarmAfterDelay(UUID technicalId, int durationMinutes) {
        try {
            logger.info("Alarm [{}] countdown started for {} minutes", technicalId, durationMinutes);
            Thread.sleep(durationMinutes * 60L * 1000L);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "EggAlarm",
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.join();
            if (node == null) {
                logger.warn("Alarm [{}] not found during countdown", technicalId);
                return;
            }
            EggAlarm alarm = objectMapper.convertValue(node, EggAlarm.class);
            if (alarm.getStatus() == AlarmStatus.SCHEDULED) {
                alarm.setStatus(AlarmStatus.RINGING);
                entityService.updateItem(
                        "EggAlarm",
                        ENTITY_VERSION,
                        technicalId,
                        alarm
                ).join();
                logger.info("Alarm [{}] is now RINGING", technicalId);

                Thread.sleep(30 * 1000L);

                alarm.setStatus(AlarmStatus.COMPLETED);
                entityService.updateItem(
                        "EggAlarm",
                        ENTITY_VERSION,
                        technicalId,
                        alarm
                ).join();
                logger.info("Alarm [{}] is now COMPLETED", technicalId);
            }
        } catch (InterruptedException e) {
            logger.error("Alarm [{}] countdown interrupted", technicalId, e);
            Thread.currentThread().interrupt();
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getReason());
        logger.error("Handled error: status={}, reason={}", ex.getStatusCode().value(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    private JsonNode callExternalApiMock() {
        try {
            String mockJson = "{\"message\":\"This is a mocked external API response.\"}";
            return objectMapper.readTree(mockJson);
        } catch (Exception e) {
            logger.error("Failed to parse mock JSON", e);
            return null;
        }
    }
}
```

---

### Summary of changes:

- Added a private workflow function `processEggAlarm` with signature:  
  `private CompletableFuture<EggAlarm> processEggAlarm(EggAlarm alarm)`  
  This method asynchronously receives the entity, can modify it if needed, and returns it back wrapped in a `CompletableFuture`.

- Updated the `entityService.addItem` call in the `createAlarm` method to pass this workflow function as the last argument:  
  ```java
  CompletableFuture<UUID> idFuture = entityService.addItem(
          "EggAlarm",
          ENTITY_VERSION,
          alarm,
          this::processEggAlarm
  );
  ```

- Added the required import for `java.util.function.Function` (which may be used by the `entityService` interface).

- Kept the rest of the code unchanged to preserve functionality.

You can customize the `processEggAlarm` method to modify the entity state or call other entity models asynchronously before persistence as per your business logic.