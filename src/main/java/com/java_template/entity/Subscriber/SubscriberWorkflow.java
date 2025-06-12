package com.java_template.entity.Subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class SubscriberWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberWorkflow.class);
    private final ObjectMapper objectMapper;

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestration method - no business logic here
    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        return processValidateEmail(entity)
                .thenCompose(this::processGenerateToken)
                .thenCompose(this::processSendConfirmationEmail)
                .thenApply(this::processMarkPending);
    }

    // Validate email format (business logic)
    private CompletableFuture<ObjectNode> processValidateEmail(ObjectNode entity) {
        String email = entity.get("email").asText("");
        if (email.isEmpty() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            logger.error("Invalid email format: {}", email);
            entity.put("status", "invalid_email");
            return CompletableFuture.completedFuture(entity);
        }
        entity.put("status", "email_validated");
        return CompletableFuture.completedFuture(entity);
    }

    // Generate confirmation token and attach to entity
    private CompletableFuture<ObjectNode> processGenerateToken(ObjectNode entity) {
        String token = java.util.UUID.randomUUID().toString();
        entity.put("confirmationToken", token);
        entity.put("status", "token_generated");
        return CompletableFuture.completedFuture(entity);
    }

    // Send confirmation email asynchronously (mocked)
    private CompletableFuture<ObjectNode> processSendConfirmationEmail(ObjectNode entity) {
        String email = entity.get("email").asText();
        String token = entity.get("confirmationToken").asText();
        logger.info("Workflow: Sending subscription confirmation email to {}", email);
        CompletableFuture.runAsync(() -> {
            // TODO: Implement real email sending logic here
            logger.info("[Email] Subscription confirmation email sent to {} with token {}", email, token);
        });
        entity.put("status", "confirmation_email_sent");
        return CompletableFuture.completedFuture(entity);
    }

    // Mark entity state as pending confirmation
    private ObjectNode processMarkPending(ObjectNode entity) {
        entity.put("entityVersion", ENTITY_VERSION);
        entity.put("workflowState", "pendingConfirmation");
        return entity;
    }
}