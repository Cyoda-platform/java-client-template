package com.java_template.entity.alarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class AlarmWorkflow {

    private final ObjectMapper objectMapper;
    private final AtomicBoolean alarmStartInProgress = new AtomicBoolean(false);

    private static final Map<String, Integer> EGG_TYPE_DURATIONS = Map.of(
            "soft", 4 * 60,
            "medium", 7 * 60,
            "hard", 10 * 60
    );

    // TODO: Inject or otherwise provide entityService
    private final EntityService entityService;

    public AlarmWorkflow(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processAlarm(ObjectNode alarm) {
        // Workflow orchestration only, invokes business logic methods below
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Starting workflow for alarm: {}", alarm);
            try {
                processPreChecks(alarm).join();
                processSetProperties(alarm);
                processScheduleCompletion(alarm);
                return alarm;
            } finally {
                alarmStartInProgress.set(false);
            }
        });
    }

    private CompletableFuture<Void> processPreChecks(ObjectNode alarm) {
        if (!alarmStartInProgress.compareAndSet(false, true)) {
            logger.warn("Alarm start already in progress, rejecting new alarm start");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another alarm start is in progress");
        }

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
            return CompletableFuture.completedFuture(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted during alarm start");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResponseStatusException) {
                throw (ResponseStatusException) cause;
            }
            logger.error("Error during alarm start workflow", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed during alarm start");
        } catch (TimeoutException e) {
            logger.error("Timeout during alarm start workflow", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Timeout during alarm start");
        }
    }

    private void processSetProperties(ObjectNode alarm) {
        String eggType = alarm.get("eggType").asText();
        int durationSeconds = EGG_TYPE_DURATIONS.getOrDefault(eggType, 4 * 60);
        alarm.put("durationSeconds", durationSeconds);
        Instant startTime = Instant.now();
        alarm.put("startTime", startTime.toString());
        alarm.put("status", "running");
    }

    private void processScheduleCompletion(ObjectNode alarm) {
        String eggType = alarm.get("eggType").asText();
        int durationSeconds = alarm.get("durationSeconds").asInt();
        String startTime = alarm.get("startTime").asText();

        ObjectNode scheduleEntity = JsonNodeFactory.instance.objectNode();
        scheduleEntity.put("eggType", eggType);
        scheduleEntity.put("durationSeconds", durationSeconds);
        scheduleEntity.put("startTime", startTime);

        entityService.addItem(
                COMPLETION_SCHEDULE_ENTITY_NAME,
                ENTITY_VERSION,
                scheduleEntity,
                this::processAlarmCompletionSchedule
        );
    }

    public CompletableFuture<ObjectNode> processAlarmCompletionSchedule(ObjectNode scheduleEntity) {
        // TODO: Implement completion schedule processing logic
        return CompletableFuture.completedFuture(scheduleEntity);
    }

    // Placeholder interface for EntityService used in this workflow
    public interface EntityService {
        CompletableFuture<ArrayNode> getItemsByCondition(String entityName, String entityVersion, String conditionJson);
        void addItem(String entityName, String entityVersion, ObjectNode entity, java.util.function.Function<ObjectNode, CompletableFuture<ObjectNode>> workflowFunction);
    }
}