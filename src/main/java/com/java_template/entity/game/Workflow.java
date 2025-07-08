package com.java_template.entity.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("game")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> fetchGames(ObjectNode entity) {
        logger.info("Starting fetchGames action");
        // TODO: Implement actual fetch logic, here just mark fetchStarted attribute
        entity.put("fetchStarted", true);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> fetchFailed(ObjectNode entity) {
        boolean failed = entity.path("fetchFailed").asBoolean(false);
        logger.info("Evaluating fetchFailed condition: {}", failed);
        // no change to entity state
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> fetchSucceeded(ObjectNode entity) {
        boolean succeeded = entity.path("fetchSucceeded").asBoolean(false);
        logger.info("Evaluating fetchSucceeded condition: {}", succeeded);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> storeGames(ObjectNode entity) {
        logger.info("Starting storeGames action");
        // TODO: Implement actual storing logic, here just mark storeStarted attribute
        entity.put("storeStarted", true);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> storeFailed(ObjectNode entity) {
        boolean failed = entity.path("storeFailed").asBoolean(false);
        logger.info("Evaluating storeFailed condition: {}", failed);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> storeSucceeded(ObjectNode entity) {
        boolean succeeded = entity.path("storeSucceeded").asBoolean(false);
        logger.info("Evaluating storeSucceeded condition: {}", succeeded);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> notifySubscribers(ObjectNode entity) {
        logger.info("Starting notifySubscribers action");
        // The processGame function logic for sending emails adapted here:
        if (!entity.hasNonNull("status") || entity.get("status").asText().isEmpty()) {
            entity.put("status", "unknown");
        }
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenCompose(subscribersArray -> {
                    List<String> emails = new ArrayList<>();
                    for (JsonNode subscriberNode : subscribersArray) {
                        String email = subscriberNode.path("email").asText(null);
                        if (email != null) {
                            emails.add(email);
                        }
                    }
                    String summary = String.format("Game update: %s vs %s, score %d-%d, status: %s",
                            entity.path("awayTeam").asText(""),
                            entity.path("homeTeam").asText(""),
                            entity.path("awayScore").asInt(-1),
                            entity.path("homeScore").asInt(-1),
                            entity.path("status").asText(""));
                    List<CompletableFuture<Void>> emailFutures = new ArrayList<>();
                    for (String email : emails) {
                        emailFutures.add(CompletableFuture.runAsync(() -> {
                            logger.info("Sending game update email to {}: {}", email, summary);
                            // Integrate actual email service here if needed
                        }));
                    }
                    return CompletableFuture.allOf(emailFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> entity);
                });
    }

    public CompletableFuture<ObjectNode> notifyFailed(ObjectNode entity) {
        boolean failed = entity.path("notifyFailed").asBoolean(false);
        logger.info("Evaluating notifyFailed condition: {}", failed);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> notifySucceeded(ObjectNode entity) {
        boolean succeeded = entity.path("notifySucceeded").asBoolean(false);
        logger.info("Evaluating notifySucceeded condition: {}", succeeded);
        return CompletableFuture.completedFuture(entity);
    }
}