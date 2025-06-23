package com.java_template.entity.Subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class SubscriberWorkflow {

    private final ObjectMapper objectMapper;

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Workflow orchestration only - no business logic here
    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        return processNormalizeStatus(entity)
                .thenCompose(this::processSomeOtherStep) // example chaining, add more as needed
                ;
    }

    // Normalize the status field to uppercase
    private CompletableFuture<ObjectNode> processNormalizeStatus(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.hasNonNull("status")) {
                String status = entity.get("status").asText();
                entity.put("status", status.toUpperCase());
            }
            return entity;
        });
    }

    // Placeholder for additional processing step
    private CompletableFuture<ObjectNode> processSomeOtherStep(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: add more business logic here if needed
            return entity;
        });
    }
}