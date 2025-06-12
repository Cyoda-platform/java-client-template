package com.java_template.entity.LanguageMentionAggregate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class LanguageMentionAggregateWorkflow {

    private final ObjectMapper objectMapper;

    public LanguageMentionAggregateWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Workflow orchestration only
    public CompletableFuture<ObjectNode> processLanguageMentionAggregate(ObjectNode entity) {
        return processValidateCount(entity)
                .thenCompose(this::processIncrementCount)
                .thenCompose(this::processWriteToStore);
    }

    private CompletableFuture<ObjectNode> processValidateCount(ObjectNode entity) {
        if (entity.has("count")) {
            long count = entity.get("count").asLong(0);
            if (count < 0) {
                entity.put("count", 0);
            }
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processIncrementCount(ObjectNode entity) {
        // Business logic placeholder for incrementing count if necessary
        // For prototype, assume no increment needed here
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processWriteToStore(ObjectNode entity) {
        // Business logic placeholder for persisting entity state
        // Actual persistence handled outside by framework after state mutation
        return CompletableFuture.completedFuture(entity);
    }
}