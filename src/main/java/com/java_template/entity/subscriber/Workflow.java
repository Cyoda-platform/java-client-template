package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("subscriber")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    public CompletableFuture<ObjectNode> hasEmail(ObjectNode entity) {
        boolean hasEmail = entity.hasNonNull("email") && !entity.get("email").asText().isBlank();
        entity.put("hasEmail", hasEmail);
        logger.info("hasEmail check: {}", hasEmail);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> normalizeEmail(ObjectNode entity) {
        JsonNode emailNode = entity.get("email");
        if (emailNode != null && !emailNode.isNull()) {
            String normalizedEmail = emailNode.asText().toLowerCase(Locale.ROOT);
            entity.put("email", normalizedEmail);
            logger.info("normalizeEmail action: normalized email to {}", normalizedEmail);
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> missingSubscribedAt(ObjectNode entity) {
        boolean missingSubscribedAt = !entity.hasNonNull("subscribedAt") || entity.get("subscribedAt").asText().isBlank();
        entity.put("missingSubscribedAt", missingSubscribedAt);
        logger.info("missingSubscribedAt check: {}", missingSubscribedAt);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> setSubscribedAt(ObjectNode entity) {
        if (!entity.hasNonNull("subscribedAt") || entity.get("subscribedAt").asText().isBlank()) {
            String today = LocalDate.now().toString();
            entity.put("subscribedAt", today);
            logger.info("setSubscribedAt action: set subscribedAt to {}", today);
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isEmailUnique(ObjectNode entity) {
        // TODO: Implement actual uniqueness check against datastore
        // For prototype, assume email is unique if no "duplicate" flag is set
        boolean isUnique = true;
        if (entity.has("duplicate") && entity.get("duplicate").asBoolean(false)) {
            isUnique = false;
        }
        entity.put("isEmailUnique", isUnique);
        logger.info("isEmailUnique check: {}", isUnique);
        return CompletableFuture.completedFuture(entity);
    }
}