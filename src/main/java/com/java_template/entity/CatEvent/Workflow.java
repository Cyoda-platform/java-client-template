package com.java_template.entity.CatEvent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("CatEvent")
public class Workflow {

    public CompletableFuture<ObjectNode> processCatEvent(ObjectNode entity) {
        try {
            if (!entity.hasNonNull("notificationStatus") || entity.get("notificationStatus").asText().isEmpty()) {
                entity.put("notificationStatus", "pending");
            }

            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500); // simulate delay

                    String eventType = entity.hasNonNull("eventType") ? entity.get("eventType").asText() : "unknown";
                    String eventDescription = entity.hasNonNull("eventDescription") ? entity.get("eventDescription").asText() : "";

                    String msg = String.format("Emergency! A cat demands snacks (Type: %s, Description: %s)", eventType, eventDescription);

                    log.info("Sending notification: {}", msg);

                    // TODO: integrate real notification channel here

                    entity.put("notificationStatus", "sent");

                } catch (InterruptedException e) {
                    log.error("Notification sending interrupted", e);
                    Thread.currentThread().interrupt();
                    entity.put("notificationStatus", "failed");
                } catch (Exception e) {
                    log.error("Error during notification sending", e);
                    entity.put("notificationStatus", "failed");
                }
            });

        } catch (Exception e) {
            log.error("Error in workflow processCatEvent", e);
            entity.put("notificationStatus", "failed");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isNotificationFailed(ObjectNode entity) {
        boolean value = entity.hasNonNull("notificationStatus") && "failed".equals(entity.get("notificationStatus").asText());
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isNotificationPending(ObjectNode entity) {
        boolean value = entity.hasNonNull("notificationStatus") && "pending".equals(entity.get("notificationStatus").asText());
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isNotificationSent(ObjectNode entity) {
        boolean value = entity.hasNonNull("notificationStatus") && "sent".equals(entity.get("notificationStatus").asText());
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }
}