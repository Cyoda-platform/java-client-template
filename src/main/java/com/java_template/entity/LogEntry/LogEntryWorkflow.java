package com.java_template.entity.LogEntry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class LogEntryWorkflow {

    private final ObjectMapper objectMapper;

    public LogEntryWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processLogEntry(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            return processSetProcessedTimestamp(entity)
                    .thenCompose(this::processAnalyzeEntry)
                    .thenCompose(this::processGenerateReport)
                    .join();
        });
    }

    private CompletableFuture<ObjectNode> processSetProcessedTimestamp(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("processedTimestamp", Instant.now().toString());
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAnalyzeEntry(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Add business logic to analyze the log entry and update entity state accordingly
            // Example placeholder:
            entity.put("analysisStatus", "completed");
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processGenerateReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Add business logic to generate a report based on analysis results in the entity
            // Example placeholder:
            entity.put("reportGenerated", true);
            return entity;
        });
    }
}