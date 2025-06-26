package com.java_template.entity.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class NotificationWorkflow {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<ObjectNode> prepareMessage(ObjectNode entity) {
        String originalMessage = entity.path("message").asText("");
        entity.put("message", "[Processed] " + originalMessage);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> validateRecipient(ObjectNode entity) {
        String recipient = entity.path("recipient").asText("");
        if (recipient.isEmpty()) {
            log.warn("Notification recipient is empty, cannot send notification");
            entity.put("status", "failed");
            entity.put("error", "Recipient is empty");
        } else {
            entity.put("status", "ready");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> sendNotification(ObjectNode entity) {
        if ("ready".equals(entity.path("status").asText())) {
            String recipient = entity.path("recipient").asText();
            String message = entity.path("message").asText();
            log.info("Sending notification asynchronously to '{}': {}", recipient, message);
            entity.put("status", "sent");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isRecipientEmpty(ObjectNode entity) {
        boolean value = entity.path("recipient").asText("").isEmpty();
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isRecipientNotEmpty(ObjectNode entity) {
        boolean value = !entity.path("recipient").asText("").isEmpty();
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }
}