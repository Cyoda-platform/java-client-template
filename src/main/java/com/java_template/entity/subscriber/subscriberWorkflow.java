package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class SubscriberWorkflow {

    private final ObjectMapper objectMapper;

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        return processValidate(entity)
                .thenCompose(this::processSendWelcomeEmail)
                .thenApply(this::processMarkCompleted);
    }

    private CompletableFuture<ObjectNode> processValidate(ObjectNode entity) {
        // Example validation logic - can be extended
        if (!entity.hasNonNull("email") || entity.get("email").asText().isEmpty()) {
            entity.put("status", "error");
            entity.put("errorMessage", "Email is missing");
            return CompletableFuture.completedFuture(entity);
        }
        entity.put("status", "validated");
        entity.put(ENTITY_VERSION, entity.has(ENTITY_VERSION) ? entity.get(ENTITY_VERSION).asInt() + 1 : 1);
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSendWelcomeEmail(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String email = entity.get("email").asText();
            logger.info("Sending welcome email to {}", email);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            logger.info("Welcome email sent to {}", email);
            entity.put("emailSent", true);
            entity.put(ENTITY_VERSION, entity.has(ENTITY_VERSION) ? entity.get(ENTITY_VERSION).asInt() + 1 : 1);
            return entity;
        });
    }

    private ObjectNode processMarkCompleted(ObjectNode entity) {
        entity.put("status", "completed");
        entity.put(ENTITY_VERSION, entity.has(ENTITY_VERSION) ? entity.get(ENTITY_VERSION).asInt() + 1 : 1);
        return entity;
    }
}