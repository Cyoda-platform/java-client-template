package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.java_template.common.config.Config.*;

@Component("subscriber")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-.]+@[\\w-]+\\.[a-z]{2,}$", Pattern.CASE_INSENSITIVE);

    // processSubscriber: Normalize email, set subscribedAt, send welcome email asynchronously
    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        String email = entity.path("email").asText(null);
        if (email != null) {
            String normalizedEmail = email.toLowerCase(Locale.ROOT).trim();
            entity.put("email", normalizedEmail);
        }
        if (!entity.hasNonNull("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
        }
        return CompletableFuture.runAsync(() -> {
            logger.info("Sending welcome email to subscriber: {}", entity.get("email").asText());
            // TODO: Integrate actual email service here if needed
        }).thenApply(v -> entity);
    }

    // isEmailValid: returns true if email field is present and matches regex
    public CompletableFuture<ObjectNode> isEmailValid(ObjectNode entity) {
        String email = entity.path("email").asText(null);
        boolean valid = email != null && EMAIL_PATTERN.matcher(email).matches();
        entity.put("success", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // isEmailInvalid: inverse of isEmailValid
    public CompletableFuture<ObjectNode> isEmailInvalid(ObjectNode entity) {
        String email = entity.path("email").asText(null);
        boolean invalid = email == null || !EMAIL_PATTERN.matcher(email).matches();
        entity.put("success", invalid);
        return CompletableFuture.completedFuture(entity);
    }

    // wantsToUnsubscribe: returns true if entity has a field "unsubscribeRequested" true or similar
    public CompletableFuture<ObjectNode> wantsToUnsubscribe(ObjectNode entity) {
        boolean wantsUnsub = entity.path("unsubscribeRequested").asBoolean(false);
        entity.put("success", wantsUnsub);
        return CompletableFuture.completedFuture(entity);
    }

    // processUnsubscribe: remove subscription date and log
    public CompletableFuture<ObjectNode> processUnsubscribe(ObjectNode entity) {
        entity.remove("subscribedAt");
        logger.info("Processed unsubscription for email: {}", entity.path("email").asText("unknown"));
        return CompletableFuture.completedFuture(entity);
    }

    // hasScoresToFetch: check if 'fetchDate' field exists and is non-empty
    public CompletableFuture<ObjectNode> hasScoresToFetch(ObjectNode entity) {
        String fetchDate = entity.path("fetchDate").asText(null);
        boolean hasDate = fetchDate != null && !fetchDate.isEmpty();
        entity.put("success", hasDate);
        return CompletableFuture.completedFuture(entity);
    }

    // fetchAndStoreScores: fetch from external API, store scores in entity attribute 'games'
    public CompletableFuture<ObjectNode> fetchAndStoreScores(ObjectNode entity) {
        String date = entity.path("fetchDate").asText(null);
        if (date == null) {
            logger.error("fetchDate missing in entity for fetchAndStoreScores");
            return CompletableFuture.completedFuture(entity);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=test", date);
                String response = restTemplate.getForObject(url, String.class);
                entity.put("gamesRawJson", response);
                logger.info("Fetched and stored scores for date {}", date);
            } catch (Exception e) {
                logger.error("Error fetching scores for date {}: {}", date, e.getMessage());
            }
            return entity;
        });
    }

    // noSubscribers: returns true if subscribers list size is 0 or missing
    public CompletableFuture<ObjectNode> noSubscribers(ObjectNode entity) {
        int count = entity.path("subscribersCount").asInt(0);
        boolean noSubs = count == 0;
        entity.put("success", noSubs);
        return CompletableFuture.completedFuture(entity);
    }

    // hasSubscribers: returns true if subscribersCount > 0
    public CompletableFuture<ObjectNode> hasSubscribers(ObjectNode entity) {
        int count = entity.path("subscribersCount").asInt(0);
        boolean hasSubs = count > 0;
        entity.put("success", hasSubs);
        return CompletableFuture.completedFuture(entity);
    }

    // sendDailyNotifications: mock sending summary emails asynchronously
    public CompletableFuture<ObjectNode> sendDailyNotifications(ObjectNode entity) {
        return CompletableFuture.runAsync(() -> {
            logger.info("Sending daily notifications to subscribers");
            // TODO: integrate actual email notification logic here
        }).thenApply(v -> entity);
    }

    // dailyScheduleTrigger: returns true if current time matches scheduled time (mocked as always true here)
    public CompletableFuture<ObjectNode> dailyScheduleTrigger(ObjectNode entity) {
        // For prototype, always return true
        entity.put("success", true);
        return CompletableFuture.completedFuture(entity);
    }
}