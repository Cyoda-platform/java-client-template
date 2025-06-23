package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetWorkflow {

    private final ObjectMapper objectMapper;

    public CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        return processInitialize(entity)
                .thenCompose(this::processValidate)
                .thenCompose(this::processSetDefaults)
                .thenCompose(this::processPostProcess);
    }

    private CompletableFuture<ObjectNode> processInitialize(ObjectNode entity) {
        // Example: set entity version
        entity.put("entityVersion", ENTITY_VERSION);
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processValidate(ObjectNode entity) {
        // Validate status presence and content
        if (!entity.hasNonNull("status") || !StringUtils.hasText(entity.get("status").asText())) {
            entity.put("status", "available");
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetDefaults(ObjectNode entity) {
        // Set createdAt if not present
        if (!entity.hasNonNull("createdAt")) {
            entity.put("createdAt", System.currentTimeMillis());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processPostProcess(ObjectNode entity) {
        // Placeholder for async fire-and-forget tasks, e.g. notifications
        // CompletableFuture.runAsync(() -> sendNotification(entity)); // TODO implement notification
        return CompletableFuture.completedFuture(entity);
    }
}