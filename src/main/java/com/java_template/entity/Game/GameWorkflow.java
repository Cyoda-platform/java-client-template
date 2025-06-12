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

    public CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        return processFetchData(entity)
                .thenCompose(this::processStoreData)
                .thenCompose(this::processSendNotifications);
    }

    private CompletableFuture<ObjectNode> processFetchData(ObjectNode entity) {
        logger.info("Workflow: Fetching NBA game scores from external API");
        // TODO: Fetch data asynchronously and update entity attributes directly
        // e.g. entity.put("scoresFetched", true);
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processStoreData(ObjectNode entity) {
        logger.info("Workflow: Storing NBA game scores locally");
        // TODO: Store data logic - modify entity state if needed
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSendNotifications(ObjectNode entity) {
        logger.info("Workflow: Sending notifications to all subscribers about new game scores");
        CompletableFuture.runAsync(() -> {
            // TODO: Access subscribers and send email notifications
            // Example:
            // subscribers.values().forEach(sub -> {
            //     logger.info("[Email] Sending daily NBA scores email to {}", sub.getEmail());
            // });
        });
        return CompletableFuture.completedFuture(entity);
    }
}