package com.java_template.entity.subscriber;

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

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode subscriberNode) {
        // Workflow orchestration only
        return processEnsureStatus(subscriberNode)
                .thenCompose(this::processSendWelcomeEmail);
    }

    private CompletableFuture<ObjectNode> processEnsureStatus(ObjectNode subscriberNode) {
        if (!subscriberNode.hasNonNull("status")) {
            subscriberNode.put("status", "pending");
        }
        return CompletableFuture.completedFuture(subscriberNode);
    }

    private CompletableFuture<ObjectNode> processSendWelcomeEmail(ObjectNode subscriberNode) {
        return CompletableFuture.runAsync(() -> {
            String email = subscriberNode.path("email").asText(null);
            if (email != null) {
                logger.info("Async sending welcome email to: {}", email);
                // TODO: real email sending logic here
            }
        }).thenApply(v -> subscriberNode);
    }
}