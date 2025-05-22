package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public CompletableFuture<ObjectNode> processUser(ObjectNode entity) {
        // Orchestrate workflow steps sequentially
        return processAddTimestamp(entity)
                .thenCompose(this::processAddSupplementaryUserRole);
    }

    private CompletableFuture<ObjectNode> processAddTimestamp(ObjectNode entity) {
        logger.info("Adding timestamp to user entity id={}", entity.path("id").asText("N/A"));
        entity.put("retrievedAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processAddSupplementaryUserRole(ObjectNode entity) {
        logger.info("Adding supplementary userRole entity asynchronously for user id={}", entity.path("id").asInt());
        return CompletableFuture.runAsync(() -> {
            try {
                ObjectNode userRole = objectMapper.createObjectNode();
                userRole.put("userId", entity.path("id").asInt());
                userRole.put("role", "basic-user");
                // Use identity function to avoid workflow recursion when adding supplementary entity
                entityService.addItem("userRole", "1.0", userRole, java.util.function.Function.identity());
            } catch (Exception ex) {
                logger.error("Error adding supplementary userRole entity", ex);
            }
        }).thenApply(v -> entity);
    }
}