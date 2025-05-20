package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    public CompletableFuture<ObjectNode> processcatFact(ObjectNode entity) {
        // Workflow orchestration only
        return processSetTimestamp(entity)
                .thenCompose(this::processSendEmails);
    }

    public CompletableFuture<ObjectNode> processSetTimestamp(ObjectNode entity) {
        if (!entity.hasNonNull("timestamp")) {
            entity.put("timestamp", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processSendEmails(ObjectNode entity) {
        // Fire-and-forget emails sending simulation
        // TODO: Replace with real email sending logic
        CompletableFuture.runAsync(() -> {
            try {
                JsonNode subscribersArray = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).get();
                for (JsonNode subscriberNode : subscribersArray) {
                    String email = subscriberNode.path("email").asText(null);
                    if (email != null && !email.isBlank()) {
                        logger.info("[Workflow] Sending cat fact email to {}", email);
                    }
                }
            } catch (Exception e) {
                logger.error("[Workflow] Failed to send cat fact emails", e);
            }
        });
        return CompletableFuture.completedFuture(entity);
    }
}