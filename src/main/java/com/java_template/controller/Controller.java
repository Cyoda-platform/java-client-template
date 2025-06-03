package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-alarm")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "alarm";
    private static final String COMPLETION_SCHEDULE_ENTITY_NAME = "alarmCompletionSchedule";

    // Executor for scheduling delayed tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Egg type durations in seconds
    private static final ConcurrentMap<String, Integer> EGG_TYPE_DURATIONS = new ConcurrentHashMap<>() {{
        put("soft", 4 * 60);
        put("medium", 7 * 60);
        put("hard", 10 * 60);
    }};

    // Prevent concurrent starts: simple in-memory guard to avoid race condition during check
    private final AtomicBoolean alarmStartInProgress = new AtomicBoolean(false);

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        logger.info("Controller initialized");
    }

    @PostMapping("/start")
    public ResponseEntity<AlarmResponse> startAlarm(@RequestBody @Valid AlarmRequest request) {
        logger.info("Received start alarm request: {}", request);
        String eggType = request.getEggType();

        if (!EGG_TYPE_DURATIONS.containsKey(eggType)) {
            logger.error("Invalid eggType: {}", eggType);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eggType: " + eggType);
        }

        // Use an ObjectNode for alarm entity with only eggType initially
        ObjectNode alarmNode = objectMapper.createObjectNode();
        alarmNode.put("eggType", eggType);

        // Call addItem without workflow function
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                alarmNode
        );

        UUID technicalId;
        try {
            technicalId = idFuture.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while starting alarm");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResponseStatusException) {
                throw (ResponseStatusException) cause;
            }
            logger.error("Error starting alarm", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start alarm");
        } catch (TimeoutException e) {
            logger.error("Timeout starting alarm", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Timeout starting alarm");
        }

        // Prepare response with approximate startTime and duration; exact values set in workflow but unavailable here
        Instant now = Instant.now();
        AlarmResponse response = new AlarmResponse(
                technicalId.toString(),
                eggType,
                EGG_TYPE_DURATIONS.get(eggType),
                now.toString(),
                "running"
        );
        logger.info("Alarm started: {}", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{alarmId}/status")
    public ResponseEntity<AlarmStatusResponse> getAlarmStatus(@PathVariable @NotBlank String alarmId) {
        logger.info("Received get alarm status request for alarmId: {}", alarmId);
        UUID uuid;
        try {
            uuid = UUID.fromString(alarmId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for alarmId: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alarmId format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                uuid
        );
        ObjectNode node;
        try {
            node = itemFuture.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while fetching alarm status");
        } catch (ExecutionException e) {
            logger.error("Error fetching alarm status", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch alarm status");
        } catch (TimeoutException e) {
            logger.error("Timeout fetching alarm status", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Timeout fetching alarm status");
        }

        if (node == null || node.isEmpty()) {
            logger.error("Alarm not found: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found");
        }

        String status = node.path("status").asText(null);
        String eggType = node.path("eggType").asText(null);
        int durationSeconds = node.path("durationSeconds").asInt(-1);
        String startTimeStr = node.path("startTime").asText(null);

        if (status == null || eggType == null || durationSeconds == -1 || startTimeStr == null) {
            logger.error("Incomplete alarm data for id: {}", alarmId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Incomplete alarm data");
        }

        Instant startTime;
        try {
            startTime = Instant.parse(startTimeStr);
        } catch (Exception e) {
            logger.error("Invalid startTime format for alarm {}", alarmId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid alarm startTime");
        }

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