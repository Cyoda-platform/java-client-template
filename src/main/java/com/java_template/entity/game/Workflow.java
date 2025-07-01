package com.java_template.entity.game;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import static com.java_template.common.config.Config.*;

@Component("game")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    // Condition function: true if entity does NOT contain a valid non-empty "date" field
    public CompletableFuture<ObjectNode> isInvalidFetchRequest(ObjectNode entity) {
        boolean invalid = true;
        if (entity.hasNonNull("date") && !entity.get("date").asText().isEmpty()) {
            invalid = false;
        }
        entity.put("isInvalidFetchRequest", invalid);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: true if entity contains a valid non-empty "date" field
    public CompletableFuture<ObjectNode> isValidFetchRequest(ObjectNode entity) {
        boolean valid = entity.hasNonNull("date") && !entity.get("date").asText().isEmpty();
        entity.put("isValidFetchRequest", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: always true
    public CompletableFuture<ObjectNode> alwaysTrue(ObjectNode entity) {
        entity.put("alwaysTrue", true);
        return CompletableFuture.completedFuture(entity);
    }

    // Action function: asynchronously fetch games and notify subscribers based on "date" field in entity
    public CompletableFuture<ObjectNode> fetchGamesAndNotifySubscribersAsync(ObjectNode entity) {
        if (!entity.hasNonNull("date")) {
            logger.error("fetchGamesAndNotifySubscribersAsync called without 'date' field");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date is required for fetch request");
        }
        String date = entity.get("date").asText();
        CompletableFuture.runAsync(() -> {
            try {
                fetchGamesAndNotifySubscribers(date);
            } catch (Exception e) {
                logger.error("Error fetching games and notifying subscribers for date {}: {}", date, e.getMessage(), e);
            }
        });
        return CompletableFuture.completedFuture(entity);
    }

    // Internal method stub to fetch games and notify subscribers
    private void fetchGamesAndNotifySubscribers(String date) throws Exception {
        logger.info("Fetching games and notifying subscribers for date {}", date);
        // TODO: Implement actual fetch and notify logic
    }
}