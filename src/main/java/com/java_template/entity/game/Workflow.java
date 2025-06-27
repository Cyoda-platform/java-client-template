package com.java_template.entity.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("game")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        // Orchestration only, no business logic here
        return fetchSubscribersAndNotify(entity)
                .thenApply(v -> {
                    entity.put("notificationSent", true);
                    return entity;
                });
    }

    private CompletableFuture<Void> fetchSubscribersAndNotify(ObjectNode gameEntity) {
        String date = gameEntity.has("date") ? gameEntity.get("date").asText() : "";
        String homeTeam = gameEntity.has("homeTeam") ? gameEntity.get("homeTeam").asText() : "";
        String awayTeam = gameEntity.has("awayTeam") ? gameEntity.get("awayTeam").asText() : "";
        int homeScore = gameEntity.has("homeScore") ? gameEntity.get("homeScore").asInt() : 0;
        int awayScore = gameEntity.has("awayScore") ? gameEntity.get("awayScore").asInt() : 0;

        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenAccept(subscribers -> {
                    if (subscribers.isEmpty()) {
                        logger.info("No subscribers found for notification about game {} vs {}", homeTeam, awayTeam);
                        return;
                    }
                    StringBuilder summary = new StringBuilder();
                    summary.append("NBA Game Notification:\n");
                    summary.append(String.format("%s vs %s on %s: %d - %d\n", homeTeam, awayTeam, date, homeScore, awayScore));
                    subscribers.forEach(subscriberNode -> {
                        JsonNode emailNode = subscriberNode.get("email");
                        if (emailNode != null && !emailNode.isNull()) {
                            String email = emailNode.asText();
                            logger.info("Sending email to {}: \n{}", email, summary);
                        }
                    });
                });
    }
}