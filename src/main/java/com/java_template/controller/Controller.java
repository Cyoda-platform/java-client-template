package com.java_template.controller;

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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-alarms")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Integer> EGG_TYPE_DURATION = Map.of(
            "soft", 4,
            "medium", 7,
            "hard", 10
    );

    public Controller(EntityService entityService) {
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

    @PostMapping
    public ResponseEntity<EggAlarm> createAlarm(@RequestBody @Valid AlarmRequest request) {
        logger.info("Received new alarm request for eggType={}", request.getEggType());

        // Prepare initial entity as ObjectNode for entityService
        ObjectNode alarmNode = objectMapper.createObjectNode();
        alarmNode.put("eggType", request.getEggType().toLowerCase(Locale.ROOT));

        // Call addItem without workflow argument
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "EggAlarm",
                ENTITY_VERSION,
                alarmNode
        );

        UUID technicalId = idFuture.join();
        if (technicalId == null) {
            logger.error("Failed to persist alarm entity");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create alarm");
        }

        // Fetch persisted entity to return full data including computed fields
        CompletableFuture<ObjectNode> persistedFuture = entityService.getItem("EggAlarm", ENTITY_VERSION, technicalId);
        ObjectNode persistedNode = persistedFuture.join();
        if (persistedNode == null) {
            logger.error("Alarm entity not found after persistence, technicalId={}", technicalId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve created alarm");
        }

        EggAlarm alarm = objectMapper.convertValue(persistedNode, EggAlarm.class);
        alarm.setTechnicalId(technicalId);

        logger.info("Alarm scheduled: technicalId={}, alarmId={}, eggType={}, duration={}min",
                technicalId, alarm.getAlarmId(), alarm.getEggType(), alarm.getDurationMinutes());

        // Start async countdown and status update tasks after persistence
        startCountdownAsync(technicalId, alarm.getDurationMinutes());

        return ResponseEntity.status(HttpStatus.CREATED).body(alarm);
    }

    private void startCountdownAsync(UUID technicalId, int durationMinutes) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Alarm [{}] countdown started for {} minutes", technicalId, durationMinutes);
                Thread.sleep(durationMinutes * 60L * 1000L);

                updateAlarmStatus(technicalId, AlarmStatus.RINGING);

                Thread.sleep(30 * 1000L);

                updateAlarmStatus(technicalId, AlarmStatus.COMPLETED);

            } catch (InterruptedException e) {
                logger.error("Alarm [{}] countdown interrupted", technicalId, e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Unexpected error in countdown for alarm [{}]", technicalId, e);
            }
        });
    }

    private void updateAlarmStatus(UUID technicalId, AlarmStatus status) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("EggAlarm", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null) {
                logger.warn("Alarm [{}] not found for status update to {}", technicalId, status);
                return;
            }
            node.put("status", status.name());

            CompletableFuture<Void> updateFuture = entityService.updateItem("EggAlarm", ENTITY_VERSION, technicalId, node);
            updateFuture.join();

            logger.info("Alarm [{}] status updated to {}", technicalId, status);
        } catch (Exception e) {
            logger.error("Failed to update alarm [{}] status to {}", technicalId, status, e);
        }
    }

    @GetMapping("/{alarmId}")
    public ResponseEntity<EggAlarm> getAlarmStatus(@PathVariable @NotBlank String alarmId) {
        logger.info("Fetching alarm status for alarmId={}", alarmId);

        String condition = String.format("alarmId = '%s'", alarmId.replace("'", "''"));
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getReason());
        logger.error("Handled error: status={}, reason={}", ex.getStatusCode().value(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

}