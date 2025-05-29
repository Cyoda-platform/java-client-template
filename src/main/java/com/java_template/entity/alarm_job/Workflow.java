package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final Map<String, AlarmJob> alarmJobMap = new ConcurrentHashMap<>();

    private static final Map<String, Integer> BOIL_LEVEL_TO_DURATION_SECONDS = Map.of(
            "soft", 300,
            "medium", 420,
            "hard", 600
    );

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public CompletableFuture<ObjectNode> processalarm_job(ObjectNode entity) {
        logger.debug("Workflow processalarm_job invoked with entity: {}", entity);

        return processValidateEntity(entity)
                .thenCompose(valid -> processCancelPrevious(entity))
                .thenCompose(canceled -> processScheduleAlarm(entity));
    }

    private CompletableFuture<ObjectNode> processValidateEntity(ObjectNode entity) {
        String boilingLevel = entity.path("boilingLevel").asText(null);
        String startTimeStr = entity.path("startTime").asText(null);
        String expectedEndTimeStr = entity.path("expectedEndTime").asText(null);

        if (boilingLevel == null || startTimeStr == null || expectedEndTimeStr == null) {
            logger.error("Missing required fields in alarm_job entity for workflow");
            return CompletableFuture.completedFuture(entity);
        }

        try {
            Instant.parse(startTimeStr);
            Instant.parse(expectedEndTimeStr);
        } catch (Exception e) {
            logger.error("Invalid time format in alarm_job entity: {}", e.getMessage());
            return CompletableFuture.completedFuture(entity);
        }

        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processCancelPrevious(ObjectNode entity) {
        AlarmJob oldJob = alarmJobMap.get("current");
        if (oldJob != null && oldJob.getScheduledFuture() != null && !oldJob.getScheduledFuture().isDone()) {
            oldJob.getScheduledFuture().cancel(false);
            logger.info("Cancelled previous scheduled alarm task");
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processScheduleAlarm(ObjectNode entity) {
        String boilingLevel = entity.path("boilingLevel").asText(null);
        String expectedEndTimeStr = entity.path("expectedEndTime").asText(null);

        Instant expectedEndTime;
        try {
            expectedEndTime = Instant.parse(expectedEndTimeStr);
        } catch (Exception e) {
            logger.error("Invalid expectedEndTime format: {}", e.getMessage());
            return CompletableFuture.completedFuture(entity);
        }

        long delaySeconds = Instant.now().until(expectedEndTime, ChronoUnit.SECONDS);
        if (delaySeconds < 0) delaySeconds = 0;

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                logger.info("Alarm notification triggered for boilingLevel '{}'", boilingLevel);
                triggerAlarmNotification(boilingLevel);
            } catch (Exception ex) {
                logger.error("Exception during alarm notification: {}", ex.getMessage(), ex);
            } finally {
                alarmJobMap.remove("current");
                logger.info("Alarm job cleared from map for boilingLevel '{}'", boilingLevel);
            }
        }, delaySeconds, TimeUnit.SECONDS);

        AlarmJob newJob = new AlarmJob(boilingLevel, Instant.now(), expectedEndTime, future, UUID.randomUUID());
        alarmJobMap.put("current", newJob);

        return CompletableFuture.completedFuture(entity);
    }

    private void triggerAlarmNotification(String boilingLevel) {
        logger.info("Alarm triggered for boilingLevel '{}'. Sending notification...", boilingLevel);
        // TODO: Replace with real notification logic
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            logger.error("Alarm notification interrupted", e);
            Thread.currentThread().interrupt();
        }
        logger.info("Alarm notification completed for boilingLevel '{}'", boilingLevel);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlarmJob {
        private String boilingLevel;
        private Instant startTime;
        private Instant expectedEndTime;
        private ScheduledFuture<?> scheduledFuture;
        private UUID technicalId;
    }
}