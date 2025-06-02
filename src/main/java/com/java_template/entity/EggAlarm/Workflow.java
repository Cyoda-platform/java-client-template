package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Workflow {
    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private static final java.util.Map<String, Integer> EGG_TYPE_DURATION = java.util.Map.of(
            "soft", 4,
            "medium", 7,
            "hard", 10
    );

    public CompletableFuture<ObjectNode> processEggAlarm(ObjectNode entity) {
        logger.debug("Workflow processEggAlarm started for entity: {}", entity);

        return processValidateEggType(entity)
                .thenCompose(this::processSetDuration)
                .thenCompose(this::processSetCreatedAt)
                .thenCompose(this::processSetRingAt)
                .thenCompose(this::processSetStatus)
                .thenCompose(this::processSetAlarmId);
    }

    private CompletableFuture<ObjectNode> processValidateEggType(ObjectNode entity) {
        if (!entity.hasNonNull("eggType") || entity.get("eggType").asText().isBlank()) {
            throw new RuntimeException("eggType must be provided and non-empty");
        }
        String eggType = entity.get("eggType").asText().toLowerCase(Locale.ROOT);
        if (!EGG_TYPE_DURATION.containsKey(eggType)) {
            throw new RuntimeException("Invalid eggType: " + eggType + ". Allowed: soft, medium, hard");
        }
        entity.put("eggType", eggType);
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetDuration(ObjectNode entity) {
        String eggType = entity.get("eggType").asText();
        int durationMinutes = EGG_TYPE_DURATION.get(eggType);
        entity.put("durationMinutes", durationMinutes);
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetCreatedAt(ObjectNode entity) {
        if (!entity.hasNonNull("createdAt") || entity.get("createdAt").isNull()) {
            entity.put("createdAt", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetRingAt(ObjectNode entity) {
        if (!entity.hasNonNull("ringAt") || entity.get("ringAt").isNull()) {
            Instant createdAt = Instant.parse(entity.get("createdAt").asText());
            int durationMinutes = entity.get("durationMinutes").asInt();
            Instant ringAt = createdAt.plusSeconds(durationMinutes * 60L);
            entity.put("ringAt", ringAt.toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetStatus(ObjectNode entity) {
        if (!entity.hasNonNull("status")) {
            entity.put("status", AlarmStatus.SCHEDULED.name());
        } else {
            String statusStr = entity.get("status").asText();
            try {
                AlarmStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                entity.put("status", AlarmStatus.SCHEDULED.name());
            }
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetAlarmId(ObjectNode entity) {
        if (!entity.hasNonNull("alarmId") || entity.get("alarmId").asText().isBlank()) {
            entity.put("alarmId", UUID.randomUUID().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    enum AlarmStatus {
        SCHEDULED,
        RINGING,
        COMPLETED
    }
}