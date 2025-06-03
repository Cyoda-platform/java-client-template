package com.java_template.entity.alarmCompletionSchedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class AlarmCompletionScheduleWorkflow {

    private final ObjectMapper objectMapper;
    // TODO: inject entityService (not shown here)
    // private final EntityService entityService;
    // TODO: inject scheduler (not shown here)
    // private final ScheduledExecutorService scheduler;

    public AlarmCompletionScheduleWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processAlarmCompletionSchedule(ObjectNode schedule) {
        // workflow orchestration only - no business logic here
        String eggType = schedule.get("eggType").asText();
        int durationSeconds = schedule.get("durationSeconds").asInt();
        String startTimeStr = schedule.get("startTime").asText();

        Instant startTime;
        try {
            startTime = Instant.parse(startTimeStr);
        } catch (Exception e) {
            log.error("Invalid startTime format in alarmCompletionSchedule: {}", startTimeStr, e);
            // return completed future with schedule unchanged
            return CompletableFuture.completedFuture(schedule);
        }

        long elapsedSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        long delaySeconds = durationSeconds - elapsedSeconds;
        if (delaySeconds < 0) delaySeconds = 0;

        scheduleCompletionTask(schedule, eggType, startTimeStr, delaySeconds);

        return CompletableFuture.completedFuture(schedule);
    }

    private void scheduleCompletionTask(ObjectNode schedule, String eggType, String startTimeStr, long delaySeconds) {
        // fire-and-forget async scheduling
        // TODO: Use injected scheduler to schedule this task
        CompletableFuture.runAsync(() -> {
            try {
                processCompleteAlarms(eggType, startTimeStr);
            } catch (Exception e) {
                log.error("Failed to complete alarm asynchronously", e);
            }
        });
    }

    public CompletableFuture<ObjectNode> processCompleteAlarms(String eggType, String startTimeStr) throws Exception {
        // Business logic moved here - fetch alarms and update status

        // TODO: Replace below mock call with real entityService call
        CompletableFuture<ArrayNode> alarmsFuture = getRunningAlarmsMock(eggType, startTimeStr);

        ArrayNode alarms = alarmsFuture.get(5, TimeUnit.SECONDS);
        if (alarms != null && alarms.size() > 0) {
            for (JsonNode alarmNode : alarms) {
                if (!alarmNode.hasNonNull("technicalId")) {
                    log.warn("Alarm entity missing technicalId, cannot update status");
                    continue;
                }
                UUID alarmId;
                try {
                    alarmId = UUID.fromString(alarmNode.get("technicalId").asText());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid technicalId format: {}", alarmNode.get("technicalId").asText());
                    continue;
                }
                ObjectNode updatedAlarm = (ObjectNode) alarmNode.deepCopy();
                updatedAlarm.put("status", "completed");

                // TODO: Replace with real entityService.updateItem call
                updateAlarmMock(alarmId, updatedAlarm).join();

                log.info("Alarm {} marked as completed", alarmId);
            }
        } else {
            log.warn("No running alarm found to complete for eggType {} startTime {}", eggType, startTimeStr);
        }
        // No modification of the passed entity here; return empty completed future
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<ArrayNode> getRunningAlarmsMock(String eggType, String startTimeStr) {
        // TODO: Replace with real call to entityService.getItemsByCondition
        // Returning empty ArrayNode for prototype
        return CompletableFuture.completedFuture(objectMapper.createArrayNode());
    }

    public CompletableFuture<Void> updateAlarmMock(UUID alarmId, ObjectNode updatedAlarm) {
        // TODO: Replace with real call to entityService.updateItem
        return CompletableFuture.completedFuture(null);
    }
}