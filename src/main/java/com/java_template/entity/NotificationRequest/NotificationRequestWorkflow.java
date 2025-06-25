package com.java_template.entity.NotificationRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Component
public class NotificationRequestWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(NotificationRequestWorkflow.class);

    private final ObjectMapper objectMapper;
    private final EntityService entityService; // TODO: Inject your entityService here

    public NotificationRequestWorkflow(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processNotificationRequest(ObjectNode notificationNode) {
        String date = notificationNode.path("date").asText(null);
        logger.info("Workflow: processing notification request for date {}", date);

        if (date == null) {
            logger.warn("NotificationRequest entity missing 'date' field.");
            return CompletableFuture.completedFuture(notificationNode);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                notificationNode = processFetchGames(notificationNode, date).get();
                notificationNode = processFetchSubscribers(notificationNode).get();

                ArrayNode gamesNode = (ArrayNode) notificationNode.get("games");
                ArrayNode subscribersNode = (ArrayNode) notificationNode.get("subscribers");

                if (gamesNode == null || gamesNode.isEmpty()) {
                    logger.info("No games found for date {}; skipping notifications.", date);
                    return notificationNode;
                }
                if (subscribersNode == null || subscribersNode.isEmpty()) {
                    logger.info("No subscribers found; skipping notifications.");
                    return notificationNode;
                }

                notificationNode = processSendNotifications(notificationNode, gamesNode, subscribersNode, date).join();

                logger.info("Notifications sent successfully for date {}", date);
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error sending notifications for date {}: {}", date, e.getMessage(), e);
            }
            return notificationNode;
        });
    }

    private CompletableFuture<ObjectNode> processFetchGames(ObjectNode entity, String date) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String condition = String.format("{\"date\":\"%s\"}", date);
                ArrayNode gamesNode = entityService.getItemsByCondition("Game", ENTITY_VERSION, condition).get();
                entity.set("games", gamesNode);
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error fetching games for date {}: {}", date, e.getMessage(), e);
                entity.putArray("games");
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processFetchSubscribers(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ArrayNode subscribersNode = entityService.getItems("Subscriber", ENTITY_VERSION).get();
                entity.set("subscribers", subscribersNode);
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error fetching subscribers: {}", e.getMessage(), e);
                entity.putArray("subscribers");
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSendNotifications(ObjectNode entity, ArrayNode gamesNode, ArrayNode subscribersNode, String date) {
        return CompletableFuture.supplyAsync(() -> {
            for (JsonNode subNode : subscribersNode) {
                String email = subNode.path("email").asText(null);
                if (email != null) {
                    logger.info("Sending notification email to {} for {} games on {}", email, gamesNode.size(), date);
                    // TODO: Implement actual email sending logic here
                }
            }
            return entity;
        });
    }
}