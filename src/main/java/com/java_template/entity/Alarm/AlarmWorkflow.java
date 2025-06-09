package com.java_template.entity.Alarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class AlarmWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(AlarmWorkflow.class);
    private final ObjectMapper objectMapper;

    public AlarmWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Workflow orchestration only
    public CompletableFuture<ObjectNode> processAlarm(ObjectNode alarm) {
        return processValidateEggType(alarm)
                .thenCompose(this::processSetTime)
                .thenCompose(this::processSetStatusAndRequestedAt)
                .thenApply(a -> {
                    logger.info("Alarm processed and ready for persistence: {}", a);
                    return a;
                });
    }

    // Validate eggType field
    private CompletableFuture<ObjectNode> processValidateEggType(ObjectNode alarm) {
        return CompletableFuture.supplyAsync(() -> {
            String eggType = alarm.get("eggType").asText();
            if (!("soft-boiled".equals(eggType) || "medium-boiled".equals(eggType) || "hard-boiled".equals(eggType))) {
                logger.error("Invalid egg type: {}", eggType);
                throw new IllegalArgumentException("Invalid egg type");
            }
            return alarm;
        });
    }

    // Set time based on eggType
    private CompletableFuture<ObjectNode> processSetTime(ObjectNode alarm) {
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
                    // This should never happen due to prior validation
                    time = 0;
            }
            alarm.put("time", time);
            return alarm;
        });
    }

    // Set status and requestedAt fields
    private CompletableFuture<ObjectNode> processSetStatusAndRequestedAt(ObjectNode alarm) {
        return CompletableFuture.supplyAsync(() -> {
            alarm.put("status", "active");
            alarm.put("requestedAt", LocalDateTime.now().toString());
            return alarm;
        });
    }

}