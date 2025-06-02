Certainly! To adhere to the new approach and best practices you described, we should move all the asynchronous logic, especially the alarm lifecycle management (delays, status updates, etc.), from the controller into the workflow function `processAlarm`.

This means:

- The controller only prepares the initial entity data and calls `addItem` with the workflow function.
- The workflow function `processAlarm` handles asynchronous tasks like waiting until alarm time, updating status to `RINGING`, then `COMPLETED`, etc.
- Since the workflow function receives the entity as an `ObjectNode`, you must manipulate the entity directly (e.g., `entity.put(...)`), and use `entityService.getItem` and `entityService.addItem/updateItem/deleteItem` only on other entityModels, never on the same entityModel (to avoid infinite recursion).
- Fire-and-forget (async) tasks and any state transitions related to the entity lifecycle belong in the workflow function.

---

Here is the updated complete Java code with all asynchronous and lifecycle logic moved into the workflow function `processAlarm`, and the controller cleaned up accordingly:

```java
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        // Pass the workflow function processAlarm to addItem
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, alarm.toObjectNode(), this::processAlarm)
                .thenApply(technicalId -> {
                    String alarmId = technicalId.toString();
                    logger.info("Alarm set with id={}, alarmTime={}", alarmId, alarmTime);
                    return ResponseEntity.ok(new AlarmResponse(alarmId, eggType.name().toLowerCase(), AlarmStatus.SET.name().toLowerCase(), now, alarmTime));
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
                    Alarm alarm = Alarm.fromObjectNode(objectNode);
                    if (alarm.getStatus() != AlarmStatus.CANCELLED && alarm.getStatus() != AlarmStatus.COMPLETED) {
                        // Update status to CANCELLED and persist immediately
                        alarm.setStatus(AlarmStatus.CANCELLED);
                        ObjectNode updatedNode = alarm.toObjectNode();
                        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, updatedNode)
                                .thenApply(updatedId ->
                                        ResponseEntity.ok(new CancelResponse(alarmId, AlarmStatus.CANCELLED.name().toLowerCase()))
                                );
                    } else {
                        // Already cancelled or completed - return current status
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

    /**
     * Workflow function applied asynchronously before persisting the entity.
     * This handles alarm lifecycle asynchronously: waits until alarm time,
     * updates status to RINGING, waits 5 seconds, updates status to COMPLETED.
     * It modifies the entity ObjectNode directly.
     */
    private CompletableFuture<ObjectNode> processAlarm(ObjectNode alarmNode) {
        logger.debug("Workflow processAlarm started for entity: {}", alarmNode);

        // Extract necessary fields from ObjectNode
        UUID technicalId = UUID.fromString(alarmNode.get("technicalId").asText());
        Instant alarmTime = Instant.parse(alarmNode.get("alarmTime").asText());
        String statusStr = alarmNode.get("status").asText();
        AlarmStatus currentStatus = AlarmStatus.valueOf(statusStr.toUpperCase());

        // If alarm is already cancelled or completed, do nothing async
        if (currentStatus == AlarmStatus.CANCELLED || currentStatus == AlarmStatus.COMPLETED) {
            logger.debug("Alarm {} is already in terminal state {}, skipping workflow", technicalId, currentStatus);
            return CompletableFuture.completedFuture(alarmNode);
        }

        // Schedule async task to handle lifecycle events
        CompletableFuture.runAsync(() -> {
            try {
                Instant now = Instant.now();
                long delayUntilAlarmMs = ChronoUnit.MILLIS.between(now, alarmTime);
                if (delayUntilAlarmMs > 0) {
                    logger.debug("Alarm {} waiting {} ms until alarmTime", technicalId, delayUntilAlarmMs);
                    Thread.sleep(delayUntilAlarmMs);
                } else {
                    logger.debug("Alarm {} alarmTime already passed, proceeding immediately", technicalId);
                }

                // Check if cancelled meanwhile by fetching fresh entity state
                ObjectNode freshNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId).join();
                if (freshNode == null || freshNode.isEmpty()) {
                    logger.warn("Alarm {} disappeared during workflow, aborting", technicalId);
                    return;
                }
                AlarmStatus freshStatus = AlarmStatus.valueOf(freshNode.get("status").asText().toUpperCase());
                if (freshStatus == AlarmStatus.CANCELLED || freshStatus == AlarmStatus.COMPLETED) {
                    logger.info("Alarm {} is in terminal state {} during workflow, aborting", technicalId, freshStatus);
                    return;
                }

                // Update status to RINGING
                freshNode.put("status", AlarmStatus.RINGING.name());
                entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, freshNode).join();
                logger.info("Alarm {} status updated to RINGING", technicalId);

                // Wait 5 seconds while ringing
                Thread.sleep(5000);

                // Check again if cancelled during ringing
                freshNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId).join();
                if (freshNode == null || freshNode.isEmpty()) {
                    logger.warn("Alarm {} disappeared during ringing, aborting", technicalId);
                    return;
                }
                freshStatus = AlarmStatus.valueOf(freshNode.get("status").asText().toUpperCase());
                if (freshStatus == AlarmStatus.CANCELLED) {
                    logger.info("Alarm {} was cancelled during ringing, aborting completion", technicalId);
                    return;
                }

                // Update status to COMPLETED
                freshNode.put("status", AlarmStatus.COMPLETED.name());
                entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, freshNode).join();
                logger.info("Alarm {} status updated to COMPLETED", technicalId);

            } catch (InterruptedException e) {
                logger.error("Alarm workflow interrupted for id={}", technicalId, e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Unexpected error in alarm workflow for id={}", technicalId, e);
            }
        }, Executors.newSingleThreadExecutor());

        // Return the original entity node (possibly modified by caller later)
        return CompletableFuture.completedFuture(alarmNode);
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

        // Convert to ObjectNode for entityService usage (you may need to adjust depending on your JSON library)
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

        // Create Alarm from ObjectNode
        public static Alarm fromObjectNode(ObjectNode node) {
            UUID technicalId = node.has("technicalId") ? UUID.fromString(node.get("technicalId").asText()) : null;
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
```

---

### Summary of the changes:

- **Moved all async lifecycle management to `processAlarm` workflow function**:
  - Wait for alarm time.
  - If not cancelled, update status to RINGING.
  - Wait 5 seconds.
  - If still not cancelled, update status to COMPLETED.
- **Controller methods no longer start async tasks or update statuses except for immediate cancel requests**.
- **`processAlarm` uses `entityService.getItem` and `updateItem` on the same entityModel but only for other entities (allowed), but NOT on the entity passed in to avoid recursion.**  
  Actually, the problem states we cannot add/update/delete the same entityModel inside workflow function to avoid infinite recursion. However, we must update the alarm status in the workflow, so this is a tricky point:

  - The problem states:
    > You cannot add/update/delete entity of the same entityModel inside workflow function (it will cause infinite recursion).

- This means **we cannot call `updateItem` on the same entityModel inside the workflow function**.

---

### Important fix for recursion problem:

Because the workflow function is applied before persisting the entity, if the workflow tries to update the same entityModel, it will cause infinite recursion.

Our workflow needs to update the alarm status to RINGING and COMPLETED asynchronously after persisting the entity for the first time.

Therefore, **the logic of updating alarm status after the initial persistence should NOT be inside the workflow function, as it will cause recursion**.

---

### Revised approach:

- The workflow function can only modify the entity **before** the initial persist (e.g., set some fields, enrich data).
- The post-persist asynchronous lifecycle updates (RINGING, COMPLETED) must be moved out of the workflow function.
- Since the controller calls `addItem` with the workflow function, we can add a **fire-and-forget async task** in the controller **right after** the `addItem` completes successfully to handle status updates.
- The `cancelAlarm` endpoint remains unchanged (it updates status directly).
- This respects the rule: Workflow function does not call `updateItem` on the same entityModel, avoiding recursion.
- The post-persist async lifecycle is still outside the controller logic but after initial persistence.

---

### Final solution:

1. **Workflow function** modifies the entity **before persistence** (e.g., can enrich data, no async delay, no updates to same entity after persist).
2. **Controller** triggers the background async lifecycle task **after** the entity is persisted (after the future completes).
3. **Cancel** endpoint updates immediately.
4. This separates concerns and respects all constraints.

---

### Updated code reflecting this final, correct approach:

```java
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        // Pass workflow function processAlarm to addItem
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, alarm.toObjectNode(), this::processAlarm)
                .thenApply(technicalId -> {
                    String alarmId = technicalId.toString();
                    logger.info("Alarm set with id={}, alarmTime={}", alarmId, alarmTime);

                    // Fire-and-forget async lifecycle management (outside workflow function!)
                    triggerAlarmLifecycle(technicalId);

                    return ResponseEntity.ok(new AlarmResponse(alarmId, eggType.name().toLowerCase(), AlarmStatus.SET.name().toLowerCase(), now, alarmTime));
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

    /**
     * Workflow function applied asynchronously before persisting the entity.
     * Can enrich or modify entity state before persistence.
     * Must NOT call add/update/delete on the same entityModel.
     */
    private CompletableFuture<ObjectNode> processAlarm(ObjectNode alarmNode) {
        logger.debug("Workflow processAlarm invoked for entity: {}", alarmNode);

        // For example, you could add some metadata or modify fields here before persistence.
        // But do NOT start async lifecycle tasks here or update same entityModel asynchronously.
        // Just return the node as is or with minor modifications.

        // Example: Add a "createdByWorkflow" flag
        alarmNode.put("createdByWorkflow", true);

        return CompletableFuture.completedFuture(alarmNode);
    }

    /**
     * Fire-and-forget async lifecycle management after initial persistence.
     * This runs outside the workflow function to avoid recursion.
     */
    private void triggerAlarmLifecycle(UUID technicalId) {
        Executors.newSingleThreadExecutor().submit(() -> {
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

                // Refresh status
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

                // Refresh status again
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
            UUID technicalId = node.has("technicalId") ? UUID.fromString(node.get("technicalId").asText()) : null;
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
```

---

### Key points:

- The **workflow function `processAlarm` only modifies the entity before persistence**.
- The **async lifecycle management (`RINGING` and `COMPLETED` updates) is triggered after persistence in the controller**.
- **Cancel endpoint updates status immediately**.
- This approach respects the requirement **not to update the same entityModel inside the workflow function**.
- The controller is "freed" from lifecycle logic except triggering the async lifecycle task after persistence.
- The workflow function is the right place for pre-persist entity modifications.

---

This is the cleanest, safest, and spec-compliant architecture considering your constraints.