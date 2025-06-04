package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.client.RestTemplate;
import static com.java_template.common.config.Config.*;

@Component
public class SubscriberWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberWorkflow.class);
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        // Workflow orchestration only - call other process* methods as needed
        return processValidateEmail(entity)
                .thenCompose(e -> processSetDefaultStatus(e))
                .thenCompose(this::processEnrichWithWelcomeCatFact)
                .thenCompose(this::processSendWelcomeEmail);
    }

    private CompletableFuture<ObjectNode> processValidateEmail(ObjectNode entity) {
        if (!entity.hasNonNull("email") || entity.get("email").asText().isBlank()) {
            CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new IllegalArgumentException("Email is required in subscriber entity"));
            return failedFuture;
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetDefaultStatus(ObjectNode entity) {
        if (!entity.hasNonNull("status") || entity.get("status").asText().isBlank()) {
            entity.put("status", "subscribed");
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processEnrichWithWelcomeCatFact(ObjectNode entity) {
        try {
            String catFactJsonStr = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            if (catFactJsonStr != null) {
                JsonNode catFactJson = objectMapper.readTree(catFactJsonStr);
                String fact = catFactJson.path("fact").asText(null);
                if (fact != null) {
                    entity.put("welcomeCatFact", fact);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch cat fact in workflow function: {}", e.getMessage());
            // ignore failure to enrich
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSendWelcomeEmail(ObjectNode entity) {
        // Fire and forget async task
        CompletableFuture.runAsync(() -> sendWelcomeEmail(entity));
        return CompletableFuture.completedFuture(entity);
    }

    @Async
    void sendWelcomeEmail(ObjectNode entity) {
        String email = entity.path("email").asText(null);
        if (email == null || email.isBlank()) {
            logger.warn("No email found in entity, skipping welcome email");
            return;
        }
        logger.info("Sending welcome email to {}", email);
        try {
            Thread.sleep(1000); // simulate email sending delay
        } catch (InterruptedException ignored) {
        }
        logger.info("Welcome email sent to {}", email);
    }
}