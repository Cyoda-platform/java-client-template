package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-alarm")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_NAME = "alarm";

    private static final ConcurrentMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final ConcurrentMap<String, Alarm> alarmCache = new ConcurrentHashMap<>(); // to keep track of scheduled tasks and statuses

    private static final ConcurrentMap<String, Integer> EGG_TYPE_DURATIONS = ConcurrentHashMap.newKeySet().isEmpty() ?
            new ConcurrentHashMap<String, Integer>() {{
                put("soft", 4 * 60);
                put("medium", 7 * 60);
                put("hard", 10 * 60);
            }} :
            new ConcurrentHashMap<String, Integer>();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @PostMapping("/start") // must be first
    public ResponseEntity<AlarmResponse> startAlarm(@RequestBody @Valid AlarmRequest request) {
        logger.info("Received start alarm request: {}", request);
        String eggType = request.getEggType();
        if (!EGG_TYPE_DURATIONS.containsKey(eggType)) {
            logger.error("Invalid eggType: {}", eggType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eggType: " + eggType);
        }

        // Check if any running alarm exists by querying entityService
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                "{\"status\":\"running\"}"
        );
        ArrayNode runningAlarms = itemsFuture.join();
        if (runningAlarms != null && runningAlarms.size() > 0) {
            logger.warn("Attempt to start a new alarm while another is running");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An alarm is already running");
        }

        int durationSeconds = EGG_TYPE_DURATIONS.get(eggType);
        Instant startTime = Instant.now();

        Alarm alarm = new Alarm();
        alarm.setEggType(eggType);
        alarm.setDurationSeconds(durationSeconds);
        alarm.setStartTime(startTime);
        alarm.setStatus("running");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                alarm
        );
        UUID technicalId = idFuture.join();
        alarm.setTechnicalId(technicalId.toString());

        ScheduledFuture<?> future = scheduler.schedule(() -> completeAlarm(technicalId), durationSeconds, TimeUnit.SECONDS);
        scheduledTasks.put(technicalId.toString(), future);
        alarmCache.put(technicalId.toString(), alarm);

        AlarmResponse response = new AlarmResponse(
                technicalId.toString(),
                eggType,
                durationSeconds,
                startTime.toString(),
                "running"
        );
        logger.info("Alarm started: {}", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{alarmId}/status") // must be first
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

    @Async
    void completeAlarm(UUID technicalId) {
        logger.info("Completing alarm: {}", technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.join();
        if (node != null && "running".equals(node.get("status").asText())) {
            Alarm alarm = new Alarm();
            alarm.setTechnicalId(technicalId.toString());
            alarm.setEggType(node.get("eggType").asText());
            alarm.setDurationSeconds(node.get("durationSeconds").asInt());
            alarm.setStartTime(Instant.parse(node.get("startTime").asText()));
            alarm.setStatus("completed");

            entityService.updateItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId,
                    alarm
            ).join();

            logger.info("Alarm {} completed. Notification should be triggered now.", technicalId);
        }
        scheduledTasks.remove(technicalId.toString());
        alarmCache.remove(technicalId.toString());
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Alarm {
        private String technicalId; // mark this field to be ignored on serialization if needed
        private String eggType;
        private int durationSeconds;
        private Instant startTime;
        private String status;
    }
}