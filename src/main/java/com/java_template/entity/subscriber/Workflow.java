package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
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

    // Condition function: validates if fetch request is valid
    public CompletableFuture<ObjectNode> isValidFetchRequest(ObjectNode entity) {
        boolean valid = entity.hasNonNull("date") && entity.get("date").isTextual();
        entity.put("validFetchRequest", valid);
        logger.info("isValidFetchRequest evaluated to {}", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: validates if subscriber entity is valid
    public CompletableFuture<ObjectNode> isValidSubscriber(ObjectNode entity) {
        boolean valid = entity.hasNonNull("email") && entity.get("email").isTextual() && !entity.get("email").asText().isBlank();
        entity.put("validSubscriber", valid);
        logger.info("isValidSubscriber evaluated to {}", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: fetch scores from external API and attach result to entity
    public CompletableFuture<ObjectNode> fetchScoresFromExternalApi(ObjectNode entity) {
        try {
            String date = entity.get("date").asText();
            String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s", date, API_KEY);
            logger.info("Fetching NBA scores from external API: {}", url);

            String rawJson = restTemplate.getForObject(url, String.class);
            if (rawJson == null) {
                logger.error("External API returned null response");
                entity.put("fetchSuccess", false);
                return CompletableFuture.completedFuture(entity);
            }
            entity.put("rawScoresJson", rawJson);
            entity.put("fetchSuccess", true);
            logger.info("Fetched scores successfully for date {}", date);
        } catch (Exception e) {
            logger.error("Exception during fetchScoresFromExternalApi", e);
            entity.put("fetchSuccess", false);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Action: store fetched scores in entity attribute (simulate persistence)
    public CompletableFuture<ObjectNode> storeFetchedScores(ObjectNode entity) {
        if (!entity.has("fetchSuccess") || !entity.get("fetchSuccess").asBoolean()) {
            logger.error("Cannot store scores - fetch was unsuccessful");
            entity.put("storeSuccess", false);
            return CompletableFuture.completedFuture(entity);
        }
        // In a real implementation, would persist here. For prototype just mark stored.
        entity.put("storeSuccess", true);
        logger.info("Stored fetched scores successfully");
        return CompletableFuture.completedFuture(entity);
    }

    // Action: send notifications to subscribers (simulate by logging)
    public CompletableFuture<ObjectNode> sendNotifications(ObjectNode entity) {
        if (!entity.has("storeSuccess") || !entity.get("storeSuccess").asBoolean()) {
            logger.error("Cannot send notifications - store was unsuccessful");
            entity.put("notifySuccess", false);
            return CompletableFuture.completedFuture(entity);
        }
        String date = entity.has("date") ? entity.get("date").asText() : "unknown";
        logger.info("Sending notifications for NBA scores of date {}", date);
        // TODO: Implement real notification sending here
        entity.put("notifySuccess", true);
        return CompletableFuture.completedFuture(entity);
    }

    // Action: normalize subscriber email to lowercase
    public CompletableFuture<ObjectNode> normalizeEmail(ObjectNode entity) {
        JsonNode emailNode = entity.get("email");
        if (emailNode != null && emailNode.isTextual()) {
            String email = emailNode.asText().toLowerCase(Locale.ROOT);
            entity.put("email", email);
            logger.info("Normalized email to lowercase: {}", email);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Action: set subscribedAt timestamp if missing
    public CompletableFuture<ObjectNode> setSubscribedAtIfMissing(ObjectNode entity) {
        if (!entity.has("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
            logger.info("Set subscribedAt timestamp");
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Action: send subscription notification (simulate via logging)
    public CompletableFuture<ObjectNode> sendSubscriptionNotification(ObjectNode entity) {
        String email = entity.has("email") ? entity.get("email").asText() : "unknown";
        logger.info("Sending subscription notification to {}", email);
        // TODO: Implement real subscription notification sending here
        entity.put("subscriptionNotificationSent", true);
        return CompletableFuture.completedFuture(entity);
    }
}