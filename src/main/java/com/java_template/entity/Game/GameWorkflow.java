package com.java_template.entity.Game;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class GameWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(GameWorkflow.class);

    public CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        // Orchestrate the workflow by chaining processing steps
        return processAddProcessingTimestamp(entity)
                .thenCompose(this::processValidate)
                .thenCompose(this::processEnrich)
                .thenCompose(this::processFinalize);
    }

    public CompletableFuture<ObjectNode> processAddProcessingTimestamp(ObjectNode entity) {
        entity.put("processedAt", System.currentTimeMillis());
        // Possibly update version from config if needed
        entity.put("entityVersion", ENTITY_VERSION);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processValidate(ObjectNode entity) {
        // Example validation logic
        if (!entity.hasNonNull("date") || !entity.hasNonNull("homeTeam") || !entity.hasNonNull("awayTeam")) {
            logger.error("Validation failed: missing required fields");
            // Could set error state or throw exception, but here just log
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processEnrich(ObjectNode entity) {
        // Example enrichment: set default status if missing
        if (!entity.hasNonNull("status")) {
            entity.put("status", "Unknown");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processFinalize(ObjectNode entity) {
        // Final workflow step, e.g. mark processed flag
        entity.put("workflowState", "processed");
        return CompletableFuture.completedFuture(entity);
    }

}