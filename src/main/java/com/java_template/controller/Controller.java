package com.java_template.controller;

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
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CreateAlarmRequest {
        @NotNull
        @Pattern(regexp = "soft-boiled|medium-boiled|hard-boiled", message = "Invalid egg type")
        private String eggType;
    }

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // Workflow function for processing the Alarm entity
    private CompletableFuture<ObjectNode> processAlarm(ObjectNode alarm) {
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

            // Set time, status, and requestedAt fields
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

        return entityService.addItem("Alarm", ENTITY_VERSION, alarm)
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

    // Example of a potential asynchronous countdown task for future implementation
    // This is not directly related to entity persistence, but could be part of the workflow
    private CompletableFuture<Void> startAlarmCountdown(ObjectNode alarm) {
        return CompletableFuture.runAsync(() -> {
            try {
                int time = alarm.get("time").asInt();
                Thread.sleep(time * 1000L); // Simulate countdown
                logger.info("Alarm countdown finished for eggType: {}", alarm.get("eggType").asText());
                // Notify user or update status as necessary
            } catch (InterruptedException e) {
                logger.error("Countdown interrupted", e);
                Thread.currentThread().interrupt();
            }
        });
    }
}