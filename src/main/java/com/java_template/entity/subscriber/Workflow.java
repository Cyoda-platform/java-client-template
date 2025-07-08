package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("subscriber")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Condition: Check if event is score fetch (dummy logic, update with actual logic)
    public CompletableFuture<ObjectNode> isScoreFetchEvent(ObjectNode entity) {
        boolean result = entity.has("eventType") && "fetch_scores".equals(entity.get("eventType").asText());
        entity.put("conditionResult", result);
        logger.info("isScoreFetchEvent: {}", result);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: Check if event is subscription (dummy logic, update with actual logic)
    public CompletableFuture<ObjectNode> isSubscriptionEvent(ObjectNode entity) {
        boolean result = entity.has("eventType") && "subscribe".equals(entity.get("eventType").asText());
        entity.put("conditionResult", result);
        logger.info("isSubscriptionEvent: {}", result);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: Process subscriber - normalize email and add subscription timestamp
    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        if (entity.has("email") && entity.get("email").isTextual()) {
            String email = entity.get("email").asText().toLowerCase(Locale.ROOT);
            entity.put("email", email);
            logger.info("Normalized subscriber email: {}", email);
        }
        if (!entity.has("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
            logger.info("Set subscribedAt timestamp");
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: Validate subscriber email format
    public CompletableFuture<ObjectNode> isValidEmail(ObjectNode entity) {
        boolean valid = false;
        if (entity.has("email") && entity.get("email").isTextual()) {
            String email = entity.get("email").asText();
            valid = email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
        }
        entity.put("conditionResult", valid);
        logger.info("isValidEmail: {}", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: Fetch NBA scores from external API and attach to entity under "scores" field
    public CompletableFuture<ObjectNode> fetchNbaScores(ObjectNode entity) {
        if (!entity.has("date") || !entity.get("date").isTextual()) {
            logger.error("fetchNbaScores: Missing or invalid 'date' field");
            return CompletableFuture.completedFuture(entity);
        }
        String date = entity.get("date").asText();
        String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test", date);
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode scoresNode = objectMapper.readTree(response);
            entity.set("scores", scoresNode);
            logger.info("Fetched NBA scores for date {}", date);
        } catch (Exception e) {
            logger.error("Error fetching NBA scores", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: Check if fetched data is valid (scores node exists and non-empty)
    public CompletableFuture<ObjectNode> isFetchedDataValid(ObjectNode entity) {
        boolean valid = false;
        if (entity.has("scores") && entity.get("scores").size() > 0) {
            valid = true;
        }
        entity.put("conditionResult", valid);
        logger.info("isFetchedDataValid: {}", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: Persist scores (for prototype just log, real persistence outside scope)
    public CompletableFuture<ObjectNode> persistScores(ObjectNode entity) {
        logger.info("Persisting scores for date {}", entity.has("date") ? entity.get("date").asText() : "unknown");
        // TODO: persistence logic here
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: Check if there are subscribers to notify (dummy check on field subscriberCount)
    public CompletableFuture<ObjectNode> hasSubscribers(ObjectNode entity) {
        boolean hasSubs = entity.has("subscriberCount") && entity.get("subscriberCount").asInt() > 0;
        entity.put("conditionResult", hasSubs);
        logger.info("hasSubscribers: {}", hasSubs);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: Send notifications (for prototype just log)
    public CompletableFuture<ObjectNode> sendNotifications(ObjectNode entity) {
        logger.info("Sending notifications for date {}", entity.has("date") ? entity.get("date").asText() : "unknown");
        // TODO: implement email sending logic asynchronously
        return CompletableFuture.completedFuture(entity);
    }

    // Action: Persist subscriber (for prototype just log)
    public CompletableFuture<ObjectNode> persistSubscriber(ObjectNode entity) {
        logger.info("Persisting subscriber with email {}", entity.has("email") ? entity.get("email").asText() : "unknown");
        // TODO: persistence logic here
        return CompletableFuture.completedFuture(entity);
    }
}