To enhance the robustness of your code by reducing the logic in controllers, you should move relevant asynchronous tasks to the `process{entity_name}` workflow function. This function is invoked right before the entity is persisted, which allows you to handle asynchronous operations and modify the entity's state.

Let's move the logic related to setting the `time` and any other potential asynchronous operations into the `processAlarm` function. Additionally, we'll convert the `Alarm` object to an `ObjectNode` to allow direct manipulation of its fields.

Below is the refactored code:

```java
package com.java_template.entity;

import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/prototype/api/alarm")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CreateAlarmRequest {
        @NotNull
        @Pattern(regexp = "soft-boiled|medium-boiled|hard-boiled", message = "Invalid egg type")
        private String eggType;
    }

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // New workflow function for processing the Alarm entity
    private CompletableFuture<ObjectNode> processAlarm(ObjectNode alarm) {
        // Example asynchronous processing logic
        return CompletableFuture.supplyAsync(() -> {
            String eggType = alarm.get("eggType").asText();
            int time;

            switch (eggType) {
                case "soft-boiled":
                    time = 300;
                    break;
                case "medium-boiled":
                    time = 420;
                    break;
                case "hard-boiled":
                    time = 600;
                    break;
                default:
                    logger.error("Invalid egg type: {}", eggType);
                    throw new IllegalArgumentException("Invalid egg type");
            }

            alarm.put("time", time);
            alarm.put("status", "active");
            alarm.put("requestedAt", LocalDateTime.now().toString());

            logger.info("Processed alarm before persistence: {}", alarm);
            return alarm;
        });
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<ObjectNode>> createAlarm(@RequestBody @Valid CreateAlarmRequest request) {
        ObjectNode alarm = objectMapper.createObjectNode();
        alarm.put("eggType", request.getEggType());

        return entityService.addItem("Alarm", ENTITY_VERSION, alarm, this::processAlarm)
                .thenApply(technicalId -> {
                    logger.info("Alarm created: {}", technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(alarm);
                });
    }

    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<ObjectNode>> getAlarmStatus(@RequestParam @NotBlank String alarmId) {
        return entityService.getItem("Alarm", ENTITY_VERSION, UUID.fromString(alarmId))
                .thenApply(alarmNode -> {
                    if (alarmNode == null) {
                        logger.error("Alarm not found: {}", alarmId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    logger.info("Alarm status retrieved: {}", alarmId);
                    return ResponseEntity.ok(alarmNode);
                });
    }

    @PostMapping("/cancel")
    public CompletableFuture<ResponseEntity<Map<String, String>>> cancelAlarm(@RequestParam @NotBlank String alarmId) {
        return entityService.deleteItem("Alarm", ENTITY_VERSION, UUID.fromString(alarmId))
                .thenApply(deletedId -> {
                    if (deletedId == null) {
                        logger.error("Alarm not found: {}", alarmId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
                    }
                    logger.info("Alarm cancelled: {}", alarmId);
                    return ResponseEntity.ok(Map.of("message", "Alarm cancelled"));
                });
    }

    // TODO: Add @Async or fire-and-forget logic for alarm countdown and notification
}
```

### Key Changes:
- **Workflow Function**: The `processAlarm` function now handles the logic for setting the `time`, `status`, and `requestedAt` fields. This function uses `CompletableFuture.supplyAsync` to perform these operations asynchronously.
- **ObjectNode**: The `Alarm` entity was changed to use `ObjectNode` to allow direct manipulation of its fields within the `processAlarm` function.
- **Controller Simplification**: The `createAlarm` endpoint is simplified by moving the logic for setting entity fields to the `processAlarm` function.

This approach improves the code structure by reducing the logic in the controller and leveraging the workflow function for asynchronous and pre-persistence operations.