package com.java_template.entity.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class NotificationWorkflow {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<ObjectNode> processNotification(ObjectNode notificationEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                notificationEntity.put("entityVersion", ENTITY_VERSION);

                // Orchestration of workflow steps without business logic:
                notificationEntity = prepareMessage(notificationEntity);
                notificationEntity = validateRecipient(notificationEntity);
                notificationEntity = sendNotification(notificationEntity);

                return notificationEntity;
            } catch (Exception e) {
                log.error("Exception in processNotification workflow", e);
                return notificationEntity;
            }
        });
    }

    private ObjectNode prepareMessage(ObjectNode entity) {
        String originalMessage = entity.path("message").asText("");
        entity.put("message", "[Processed] " + originalMessage);
        return entity;
    }

    private ObjectNode validateRecipient(ObjectNode entity) {
        String recipient = entity.path("recipient").asText("");
        if (recipient.isEmpty()) {
            log.warn("Notification recipient is empty, cannot send notification");
            entity.put("status", "failed");
            entity.put("error", "Recipient is empty");
        } else {
            entity.put("status", "ready");
        }
        return entity;
    }

    private ObjectNode sendNotification(ObjectNode entity) {
        if ("ready".equals(entity.path("status").asText())) {
            String recipient = entity.path("recipient").asText();
            String message = entity.path("message").asText();
            // Simulate async sending logic here (fire and forget)
            log.info("Sending notification asynchronously to '{}': {}", recipient, message);
            // TODO: Replace with real sending logic (e.g. HTTP call, message queue, etc.)
            entity.put("status", "sent");
        }
        return entity;
    }
}