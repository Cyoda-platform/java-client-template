package com.java_template.entity.Subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class SubscriberWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberWorkflow.class);

    private final ObjectMapper objectMapper;

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        logger.info("Workflow orchestration started for subscriber: {}", entity);

        return processNormalizeEmail(entity)
                .thenCompose(this::processSendWelcomeEmail);
    }

    private CompletableFuture<ObjectNode> processNormalizeEmail(ObjectNode entity) {
        if (entity.hasNonNull("email")) {
            String email = entity.get("email").asText().toLowerCase(Locale.ROOT);
            entity.put("email", email);
            logger.info("Normalized subscriber email to lowercase: {}", email);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSendWelcomeEmail(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String email = entity.path("email").asText(null);
            if (email != null) {
                logger.info("Sending welcome email to {}", email);
                // TODO: implement actual email sending logic here
            }
            return entity;
        });
    }
}