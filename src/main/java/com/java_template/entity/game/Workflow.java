package com.java_template.entity.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.*;

@Component("game")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Condition function: checks if fetch was successful by verifying presence of homeScore and awayScore.
     */
    public CompletableFuture<ObjectNode> isFetchSuccessful(ObjectNode entity) {
        boolean success = entity.hasNonNull("homeScore") && entity.hasNonNull("awayScore");
        entity.put("success", success);
        logger.info("isFetchSuccessful: {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Action function: stores games - here just marks entity stored attribute.
     * Actual persistence is handled by Cyoda platform.
     */
    public CompletableFuture<ObjectNode> storeGames(ObjectNode entity) {
        entity.put("stored", true);
        logger.info("storeGames: entity marked as stored");
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Action function: logs fetch failure.
     */
    public CompletableFuture<ObjectNode> logFetchFailure(ObjectNode entity) {
        logger.error("Fetch failed for entity: {}", entity);
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Condition function: checks if subscribers exist.
     * Sets entity attribute 'hasSubscribers'.
     */
    public CompletableFuture<ObjectNode> hasSubscribers(ObjectNode entity) {
        try {
            CompletableFuture<ArrayNode> subsFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
            ArrayNode subs = subsFuture.get(10, TimeUnit.SECONDS);
            boolean hasSubs = subs != null && subs.size() > 0;
            entity.put("hasSubscribers", hasSubs);
            logger.info("hasSubscribers: {}", hasSubs);
        } catch (Exception e) {
            logger.error("Failed to check subscribers", e);
            entity.put("hasSubscribers", false);
        }
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Action function: sends notifications to subscribers.
     * Sets entity attribute 'notificationSent' to true if successful.
     */
    public CompletableFuture<ObjectNode> sendNotificationsForGame(ObjectNode entity) {
        try {
            String date = safeText(entity, "date");
            String homeTeam = safeText(entity, "homeTeam");
            String awayTeam = safeText(entity, "awayTeam");
            Integer homeScore = safeInt(entity, "homeScore");
            Integer awayScore = safeInt(entity, "awayScore");
            String status = safeText(entity, "status");

            StringBuilder content = new StringBuilder();
            content.append("NBA Score Update for ").append(date).append(":\n");
            content.append(String.format("%s vs %s: %s-%s (%s)\n",
                    homeTeam != null ? homeTeam : "Unknown",
                    awayTeam != null ? awayTeam : "Unknown",
                    homeScore != null ? homeScore : "N/A",
                    awayScore != null ? awayScore : "N/A",
                    status != null ? status : "Unknown"));

            CompletableFuture<ArrayNode> subsFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
            ArrayNode subs = subsFuture.get(10, TimeUnit.SECONDS);

            if (subs != null && subs.size() > 0) {
                for (JsonNode subNode : subs) {
                    JsonNode emailNode = subNode.get("email");
                    if (emailNode != null && emailNode.isTextual()) {
                        String email = emailNode.asText();
                        logger.info("Send email to {}:\n{}", email, content.toString());
                    }
                }
            }
            entity.put("notificationSent", true);
        } catch (Exception e) {
            logger.error("Failed to send notifications", e);
            entity.put("notificationSent", false);
        }
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Condition function: checks if notification was sent successfully.
     */
    public CompletableFuture<ObjectNode> isNotificationSent(ObjectNode entity) {
        boolean sent = entity.has("notificationSent") && entity.get("notificationSent").asBoolean(false);
        entity.put("notificationSentStatus", sent);
        logger.info("isNotificationSent: {}", sent);
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Action function: logs notification failure.
     */
    public CompletableFuture<ObjectNode> logNotifyFailure(ObjectNode entity) {
        logger.error("Notification failed for entity: {}", entity);
        return CompletableFuture.completedFuture(entity);
    }

    // Utility methods for safe extraction from ObjectNode

    private String safeText(ObjectNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private Integer safeInt(ObjectNode node, String field) {
        JsonNode child = node.get(field);
        if (child != null && child.isInt()) return child.asInt();
        if (child != null && child.isTextual()) {
            try {
                return Integer.parseInt(child.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    // TODO: Inject entityService or define it appropriately; 
    // placeholder so code compiles:
    private final EntityService entityService = new EntityService();

    // Dummy EntityService class to allow compilation - replace with actual implementation
    private static class EntityService {
        CompletableFuture<ArrayNode> getItems(String entityName, String version) {
            return CompletableFuture.completedFuture(null); // TODO: Implement actual fetching logic
        }
    }
}