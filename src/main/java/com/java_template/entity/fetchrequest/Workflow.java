package com.java_template.entity.fetchrequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("fetchrequest")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    // Condition function: returns true if entity contains valid fetch request with non-empty "date"
    public CompletableFuture<ObjectNode> isValidFetchRequest(ObjectNode entity) {
        boolean valid = entity.hasNonNull("date") && !entity.get("date").asText().isEmpty();
        entity.put("isValidFetchRequest", valid);
        logger.info("isValidFetchRequest evaluated to {}", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Action function: asynchronously fetch games and notify subscribers based on "date" in entity
    public CompletableFuture<ObjectNode> fetchGamesAndNotifySubscribersAsync(ObjectNode entity) {
        if (!entity.hasNonNull("date") || entity.get("date").asText().isEmpty()) {
            logger.error("fetchGamesAndNotifySubscribersAsync called with invalid or missing date");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date is required for fetch request");
        }
        String date = entity.get("date").asText();
        logger.info("Starting async fetch and notify for date {}", date);

        CompletableFuture.runAsync(() -> {
            try {
                fetchGamesAndNotifySubscribers(date);
            } catch (Exception e) {
                logger.error("Error in fetchGamesAndNotifySubscribers for date {}: {}", date, e.getMessage(), e);
            }
        });

        return CompletableFuture.completedFuture(entity);
    }

    // Internal method to fetch games and notify subscribers (mocked here - implement as needed)
    private void fetchGamesAndNotifySubscribers(String date) {
        // TODO: Implement actual fetch from external API and notify subscribers
        logger.info("Fetching games and notifying subscribers for date {}", date);
    }

}