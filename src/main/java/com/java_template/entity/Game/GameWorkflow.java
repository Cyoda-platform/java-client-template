package com.java_template.entity.Game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class GameWorkflow {

    private final ObjectMapper objectMapper;

    public GameWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity)
                .thenCompose(this::processValidateDate)
                .thenCompose(this::processUpdateScores)
                .thenCompose(this::processPrepareNotification)
                .thenApply(e -> {
                    e.put("entityVersion", ENTITY_VERSION);
                    return e;
                });
    }

    // Validate and normalize date format
    public CompletableFuture<ObjectNode> processValidateDate(ObjectNode entity) {
        if (entity.has("date")) {
            String dateStr = entity.get("date").asText();
            try {
                LocalDate date = LocalDate.parse(dateStr);
                entity.put("date", date.toString());
            } catch (DateTimeParseException ignored) {
                // leave as is if invalid
            }
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Example placeholder for business logic that updates scores or related fields
    public CompletableFuture<ObjectNode> processUpdateScores(ObjectNode entity) {
        // TODO: Add score calculation, validation, or enrichment if needed
        return CompletableFuture.completedFuture(entity);
    }

    // Example placeholder for preparing notification content or flags
    public CompletableFuture<ObjectNode> processPrepareNotification(ObjectNode entity) {
        // TODO: Add logic that marks entity ready for notification or summary creation
        return CompletableFuture.completedFuture(entity);
    }
}