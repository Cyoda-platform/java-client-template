package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("subscriber")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    // Condition function: returns true if entityData is invalid fetch request (missing or empty date)
    public CompletableFuture<ObjectNode> isInvalidFetchRequest(ObjectNode entity) {
        boolean invalid = true;
        if (entity.hasNonNull("date") && !entity.get("date").asText().isEmpty()) {
            invalid = false;
        }
        entity.put("isInvalidFetchRequest", invalid);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: returns true if entityData is valid fetch request (has non-empty date)
    public CompletableFuture<ObjectNode> isValidFetchRequest(ObjectNode entity) {
        boolean valid = entity.hasNonNull("date") && !entity.get("date").asText().isEmpty();
        entity.put("isValidFetchRequest", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: always returns true
    public CompletableFuture<ObjectNode> alwaysTrue(ObjectNode entity) {
        entity.put("alwaysTrue", true);
        return CompletableFuture.completedFuture(entity);
    }

    // Action function: asynchronously fetch games and notify subscribers based on date in entity
    public CompletableFuture<ObjectNode> fetchGamesAndNotifySubscribersAsync(ObjectNode entity) {
        if (!entity.hasNonNull("date")) {
            logger.error("fetchGamesAndNotifySubscribersAsync invoked without date");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date is required for fetch request");
        }
        String date = entity.get("date").asText();
        CompletableFuture.runAsync(() -> {
            try {
                fetchGamesAndNotifySubscribers(date);
            } catch (Exception e) {
                logger.error("Error in fetchGamesAndNotifySubscribers for date {}: {}", date, e.getMessage(), e);
            }
        });
        return CompletableFuture.completedFuture(entity);
    }

    // The actual method that fetches games and sends notifications (mocked here)
    private void fetchGamesAndNotifySubscribers(String date) throws Exception {
        logger.info("Fetching games and notifying subscribers for date {}", date);
        // TODO: implement fetching from external API, storing games, notifying subscribers
    }
}