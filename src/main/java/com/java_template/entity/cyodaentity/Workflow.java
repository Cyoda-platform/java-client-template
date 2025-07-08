package com.java_template.entity.cyodaentity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("cyodaentity")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    // Placeholder for entityService - assumed available in actual runtime environment
    private EntityService entityService;

    // Workflow function: Normalize subscriber email and add subscription timestamp
    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        if (entity.has("email") && entity.get("email").isTextual()) {
            String email = entity.get("email").asText().toLowerCase(Locale.ROOT);
            entity.put("email", email);
        }
        if (!entity.has("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
        }
        logger.info("Processed subscriber email normalization and timestamp");
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: Check if email is present and valid format
    public boolean isValidEmail(ObjectNode entity) {
        if (!entity.has("email") || !entity.get("email").isTextual()) {
            logger.info("Email not present or not textual");
            return false;
        }
        String email = entity.get("email").asText();
        boolean valid = email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
        logger.info("Email validation result for {}: {}", email, valid);
        return valid;
    }

    // Condition function: Determine if event is a subscription event (placeholder)
    public boolean isSubscriptionEvent(ObjectNode entity) {
        // TODO: Implement actual event detection logic
        return entity.has("eventType") && "subscription".equalsIgnoreCase(entity.get("eventType").asText());
    }

    // Condition function: Determine if event is a score fetch event (placeholder)
    public boolean isScoreFetchEvent(ObjectNode entity) {
        // TODO: Implement actual event detection logic
        return entity.has("eventType") && "scoreFetch".equalsIgnoreCase(entity.get("eventType").asText());
    }

    // Workflow function: Fetch NBA scores from external API and attach to entity
    public CompletableFuture<ObjectNode> fetchNbaScores(ObjectNode entity) {
        try {
            String date = entity.has("date") ? entity.get("date").asText() : null;
            if (date == null) {
                logger.error("fetchNbaScores: date field missing in entity");
                return CompletableFuture.completedFuture(entity);
            }
            String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test", date);
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode jsonNode = entity.objectNode().objectNode().objectNode().objectNode().objectNode().objectNode().objectNode().objectNode().objectNode();
                jsonNode = entity.objectNode().objectNode();
                entity.putPOJO("scoresRaw", jsonNode);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode scoresNode = mapper.readTree(response);
                entity.set("scoresRaw", scoresNode);
                logger.info("Fetched NBA scores for date {}", date);
            } else {
                logger.error("fetchNbaScores: Received null response from external API");
            }
        } catch (Exception e) {
            logger.error("Exception during fetchNbaScores", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: Check if fetched data is valid and non-empty
    public boolean isFetchedDataValid(ObjectNode entity) {
        if (!entity.has("scoresRaw")) {
            logger.info("No scoresRaw field found in entity");
            return false;
        }
        JsonNode scores = entity.get("scoresRaw");
        boolean valid = scores.isArray() && scores.size() > 0;
        logger.info("Fetched data valid: {}", valid);
        return valid;
    }

    // Workflow function: Persist scores - in prototype just log and mark timestamp
    public CompletableFuture<ObjectNode> persistScores(ObjectNode entity) {
        entity.put("persistedAt", Instant.now().toString());
        logger.info("Persisted scores data (prototype)");
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: Check if there are any subscribers
    public boolean hasSubscribers(ObjectNode entity) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND");
            CompletableFuture<ArrayNode> subsFuture = entityService.getItemsByCondition("Subscriber", ENTITY_VERSION, condition);
            ArrayNode subsArray = subsFuture.get();
            boolean hasSubs = subsArray != null && subsArray.size() > 0;
            logger.info("Subscriber count check: hasSubscribers={}", hasSubs);
            return hasSubs;
        } catch (Exception e) {
            logger.error("Exception in hasSubscribers check", e);
            return false;
        }
    }

    // Workflow function: Send notifications asynchronously
    public CompletableFuture<ObjectNode> sendNotifications(ObjectNode entity) {
        CompletableFuture.runAsync(() -> {
            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND");
                CompletableFuture<ArrayNode> subsFuture = entityService.getItemsByCondition("Subscriber", ENTITY_VERSION, condition);
                ArrayNode subsArray = subsFuture.get();

                List<String> emails = new ArrayList<>();
                subsArray.forEach(subNode -> {
                    JsonNode emailNode = subNode.get("email");
                    if (emailNode != null && emailNode.isTextual()) {
                        emails.add(emailNode.asText());
                    }
                });

                String homeTeam = entity.path("homeTeam").asText("Unknown");
                String awayTeam = entity.path("awayTeam").asText("Unknown");
                String date = entity.path("date").asText("UnknownDate");
                String homeScore = entity.hasNonNull("homeScore") ? entity.get("homeScore").asText() : "?";
                String awayScore = entity.hasNonNull("awayScore") ? entity.get("awayScore").asText() : "?";

                String notification = String.format("NBA Score for %s: %s vs %s => %s - %s", date, homeTeam, awayTeam, homeScore, awayScore);

                for (String email : emails) {
                    // TODO: Implement actual email sending
                    logger.info("Sending notification to {}: {}", email, notification);
                }
                logger.info("Notifications sent for game on {}", date);
            } catch (Exception e) {
                logger.error("Error during notification sending in workflow", e);
            }
        });
        return CompletableFuture.completedFuture(entity);
    }
}