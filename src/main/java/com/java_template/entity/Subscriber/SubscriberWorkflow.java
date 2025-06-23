package com.java_template.entity.Subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Component
public class SubscriberWorkflow {

    private final ObjectMapper objectMapper;
    private final EntityService entityService; // assume injected

    public SubscriberWorkflow(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode subscriberNode) {
        // Orchestration only - no business logic here
        return processValidateEmail(subscriberNode)
                .thenCompose(this::processCheckDuplicate)
                .thenCompose(this::processSetSubscribeDate)
                .thenCompose(this::processInitializeMetrics)
                .thenApply(node -> subscriberNode);
    }

    private CompletableFuture<ObjectNode> processValidateEmail(ObjectNode entity) {
        String email = entity.path("email").asText(null);
        if (email == null || email.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Email missing in subscriber entity"));
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processCheckDuplicate(ObjectNode entity) {
        String email = entity.path("email").asText();
        String condition = String.format("{\"email\":\"%s\"}", email);
        return entityService.getItemsByCondition("Subscriber", ENTITY_VERSION, condition)
                .thenCompose(existingArray -> {
                    if (!existingArray.isEmpty()) {
                        return CompletableFuture.failedFuture(
                                new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Email already subscribed"));
                    }
                    return CompletableFuture.completedFuture(entity);
                });
    }

    private CompletableFuture<ObjectNode> processSetSubscribeDate(ObjectNode entity) {
        entity.put("subscribedAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processInitializeMetrics(ObjectNode entity) {
        String email = entity.path("email").asText();
        ObjectNode metricsNode = objectMapper.createObjectNode();
        metricsNode.put("subscriberEmail", email);
        metricsNode.put("emailsSent", 0);
        metricsNode.put("emailOpens", 0);
        metricsNode.put("linkClicks", 0);

        return entityService.addItem("InteractionMetrics", ENTITY_VERSION, metricsNode, this::processInteractionMetrics)
                .thenApply(metricsId -> {
                    // Log inside workflow if needed
                    // No changes to current entity here
                    return entity;
                });
    }

    private CompletableFuture<ObjectNode> processInteractionMetrics(ObjectNode interactionMetricsNode) {
        // Placeholder for any processing logic if needed
        return CompletableFuture.completedFuture(interactionMetricsNode);
    }
}