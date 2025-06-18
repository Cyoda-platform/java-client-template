package com.java_template.entity.IngestionJob;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class IngestionJobWorkflow {

    private final ObjectMapper objectMapper;

    public IngestionJobWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processIngestionJob(ObjectNode entity) {
        return processStart(entity)
                .thenCompose(this::processIngestionWork)
                .thenCompose(this::processComplete)
                .exceptionally(ex -> {
                    entity.put("status", "failed");
                    entity.put("errorMessage", ex.getMessage());
                    return entity;
                });
    }

    private CompletableFuture<ObjectNode> processStart(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity == null) return null;
            entity.put("status", "processing");
            entity.put("startedAt", Instant.now().toString());
            entity.put("entityVersion", ENTITY_VERSION);
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processIngestionWork(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(2000); // Simulate ingestion work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processComplete(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("status", "completed");
            entity.put("completedAt", Instant.now().toString());
            return entity;
        });
    }
}