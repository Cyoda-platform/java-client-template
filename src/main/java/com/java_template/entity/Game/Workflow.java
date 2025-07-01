package com.java_template.entity.Game;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("Game")
public class Workflow {

    // Fetch game data from external API (mocked for prototype)
    public CompletableFuture<ObjectNode> fetchGameData(ObjectNode entity) {
        try {
            entity.put("fetchedAt", OffsetDateTime.now().toString());
            // TODO: Add actual fetching logic here
        } catch (Exception e) {
            log.error("Error in fetchGameData workflow", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Process game data - mark processedAt and send notification async
    public CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        try {
            entity.put("processedAt", OffsetDateTime.now().toString());
            sendNotificationAsync(entity); // Fire and forget
        } catch (Exception e) {
            log.error("Error in processGame workflow", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Store game data (mocked)
    public CompletableFuture<ObjectNode> storeGameData(ObjectNode entity) {
        try {
            entity.put("storedAt", OffsetDateTime.now().toString());
            // TODO: Add actual persistence logic here
        } catch (Exception e) {
            log.error("Error in storeGameData workflow", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Send notification (mocked)
    public CompletableFuture<ObjectNode> sendNotification(ObjectNode entity) {
        try {
            entity.put("notifiedAt", OffsetDateTime.now().toString());
            // TODO: Add actual notification sending here
        } catch (Exception e) {
            log.error("Error in sendNotification workflow", e);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: check if entity is fetched
    public boolean isFetched(ObjectNode entity) {
        return entity.hasNonNull("fetchedAt");
    }

    // Condition: check if entity is processed
    public boolean isProcessed(ObjectNode entity) {
        return entity.hasNonNull("processedAt");
    }

    // Condition: check if entity is stored
    public boolean isStored(ObjectNode entity) {
        return entity.hasNonNull("storedAt");
    }

    // Async notification sending stub
    private void sendNotificationAsync(ObjectNode entity) {
        // TODO: Implement real async notification sending
        log.info("sendNotificationAsync called for entity: {}", entity);
    }
}