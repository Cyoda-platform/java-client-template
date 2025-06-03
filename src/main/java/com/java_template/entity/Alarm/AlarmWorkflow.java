package com.java_template.entity.Alarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmWorkflow {

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        log.info("AlarmWorkflow initialized");
    }

    public CompletableFuture<ObjectNode> processAlarm(ObjectNode entity) {
        // Workflow orchestration only, call other process methods as needed
        return processInitialize(entity)
            .thenCompose(this::processSetTriggerAt)
            .thenCompose(this::processComplete);
    }

    private CompletableFuture<ObjectNode> processInitialize(ObjectNode entity) {
        if (!entity.hasNonNull("createdAt")) {
            entity.put("createdAt", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetTriggerAt(ObjectNode entity) {
        int delaySeconds = entity.path("setTimeSeconds").asInt(0);
        long triggerAtEpoch = Instant.now().plusSeconds(delaySeconds).toEpochMilli();
        entity.put("triggerAt", triggerAtEpoch);
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processComplete(ObjectNode entity) {
        // No business logic here, placeholder for possible future extension
        return CompletableFuture.completedFuture(entity);
    }
}