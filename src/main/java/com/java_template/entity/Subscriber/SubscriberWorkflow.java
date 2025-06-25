package com.java_template.entity.Subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import static com.java_template.common.config.Config.*;

@Component
public class SubscriberWorkflow {

    private final ObjectMapper objectMapper;

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity)
                .thenCompose(this::processNormalizeEmail)
                .thenCompose(this::processSetSubscribedAt)
                .thenCompose(this::processSetEntityVersion);
    }

    private CompletableFuture<ObjectNode> processNormalizeEmail(ObjectNode entity) {
        if (entity.has("email")) {
            String email = entity.get("email").asText().toLowerCase(Locale.ROOT);
            entity.put("email", email);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetSubscribedAt(ObjectNode entity) {
        if (!entity.has("subscribedAt")) {
            entity.put("subscribedAt", System.currentTimeMillis());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetEntityVersion(ObjectNode entity) {
        entity.put("entityVersion", ENTITY_VERSION);
        return CompletableFuture.completedFuture(entity);
    }
}