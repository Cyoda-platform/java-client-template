package com.java_template.entity.Game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class GameWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(GameWorkflow.class);
    private final ObjectMapper objectMapper;

    public GameWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processGame(ObjectNode gameNode) {
        logger.info("Workflow: processing game {}", gameNode);

        return processCalculateScoreDifference(gameNode)
                .thenCompose(this::processCreateNotificationRequest);
    }

    private CompletableFuture<ObjectNode> processCalculateScoreDifference(ObjectNode gameNode) {
        if (gameNode.hasNonNull("homeScore") && gameNode.hasNonNull("awayScore")) {
            int homeScore = gameNode.get("homeScore").asInt();
            int awayScore = gameNode.get("awayScore").asInt();
            int scoreDiff = Math.abs(homeScore - awayScore);
            gameNode.put("scoreDifference", scoreDiff);
            logger.info("Calculated scoreDifference: {}", scoreDiff);
        }
        return CompletableFuture.completedFuture(gameNode);
    }

    private CompletableFuture<ObjectNode> processCreateNotificationRequest(ObjectNode gameNode) {
        String date = gameNode.path("date").asText(null);
        if (date != null) {
            ObjectNode notificationRequest = objectMapper.createObjectNode();
            notificationRequest.put("date", date);
            // TODO: trigger NotificationRequest processing externally; no direct calls here
            logger.info("Created NotificationRequest for date {}", date);
        }
        return CompletableFuture.completedFuture(gameNode);
    }

}