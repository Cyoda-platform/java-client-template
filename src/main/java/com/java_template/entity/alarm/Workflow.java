package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class Workflow {
    private final Logger logger = LoggerFactory.getLogger(Workflow.class);

    // Orchestration method: coordinates the workflow steps, no business logic here
    public CompletableFuture<ObjectNode> processAlarm(ObjectNode alarmNode) {
        logger.debug("Workflow processAlarm started for entity: {}", alarmNode);

        return processInitialize(alarmNode)
                .thenCompose(this::processWaitForAlarm)
                .thenCompose(this::processTriggerRinging)
                .thenCompose(this::processComplete)
                .exceptionally(ex -> {
                    logger.error("Error in workflow processAlarm", ex);
                    // Optionally set error attributes on alarmNode
                    alarmNode.put("error", ex.getMessage());
                    return alarmNode;
                });
    }

    // Initializes the alarm - sets flags, timestamps, etc.
    private CompletableFuture<ObjectNode> processInitialize(ObjectNode alarmNode) {
        logger.debug("processInitialize invoked");
        alarmNode.put("status", "set");
        alarmNode.put("createdByWorkflow", true);
        alarmNode.put("setTime", System.currentTimeMillis());
        // Assume alarmTime is already set on alarmNode from outside
        return CompletableFuture.completedFuture(alarmNode);
    }

    // Simulates waiting until alarmTime
    private CompletableFuture<ObjectNode> processWaitForAlarm(ObjectNode alarmNode) {
        logger.debug("processWaitForAlarm invoked");
        long now = System.currentTimeMillis();
        long alarmTime = alarmNode.path("alarmTime").asLong(0);
        long delay = alarmTime - now;
        if (delay <= 0) delay = 0;

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for alarmTime", e);
                Thread.currentThread().interrupt();
            }
            return alarmNode;
        });
    }

    // Changes status to ringing and simulates ringing duration
    private CompletableFuture<ObjectNode> processTriggerRinging(ObjectNode alarmNode) {
        logger.debug("processTriggerRinging invoked");
        alarmNode.put("status", "ringing");

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(5000); // simulate ringing for 5 seconds
            } catch (InterruptedException e) {
                logger.error("Interrupted during ringing", e);
                Thread.currentThread().interrupt();
            }
            return alarmNode;
        });
    }

    // Marks alarm as completed
    private CompletableFuture<ObjectNode> processComplete(ObjectNode alarmNode) {
        logger.debug("processComplete invoked");
        alarmNode.put("status", "completed");
        return CompletableFuture.completedFuture(alarmNode);
    }
}