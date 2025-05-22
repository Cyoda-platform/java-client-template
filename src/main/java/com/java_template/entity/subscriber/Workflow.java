package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    // Assume subscribers cache and refreshSubscribersCache method exist
    // Assume subscribers is a ConcurrentMap<String, Subscriber>
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        // Workflow orchestration only
        return processNormalizeEmail(entity)
            .thenCompose(this::processUpdateSubscriberCache)
            .exceptionally(ex -> {
                logger.error("Error in subscriber workflow orchestration", ex);
                return entity;
            });
    }

    private CompletableFuture<ObjectNode> processNormalizeEmail(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String email = entity.path("email").asText(null);
            if (email != null) {
                email = email.toLowerCase().trim();
                entity.put("email", email);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processUpdateSubscriberCache(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                refreshSubscribersCache();
                String email = entity.path("email").asText(null);
                boolean active = entity.path("active").asBoolean(false);
                if (email != null) {
                    subscribers.put(email, new Subscriber(email, active));
                    logger.info("Workflow updated subscribers cache for email: {}", email);
                }
            } catch (Exception e) {
                logger.error("Failed to refresh subscribers cache in subscriber workflow", e);
            }
            return entity;
        });
    }

    // TODO: Define subscribers cache and refreshSubscribersCache method here or inject them
    // private ConcurrentMap<String, Subscriber> subscribers;
    // private void refreshSubscribersCache() { ... }
}