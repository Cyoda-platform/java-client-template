package com.java_template.entity.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class UserWorkflow {
    private final Logger logger = LoggerFactory.getLogger(UserWorkflow.class);
    private final ObjectMapper objectMapper;

    public UserWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processUser(ObjectNode userNode) {
        logger.info("Starting workflow orchestration for user entity: {}", userNode);

        return processEnrichUser(userNode)
                .thenCompose(this::processValidateUser)
                .thenCompose(this::processFinalizeUser);
    }

    private CompletableFuture<ObjectNode> processEnrichUser(ObjectNode userNode) {
        return CompletableFuture.supplyAsync(() -> {
            // Example enrichment: add processed timestamp and entity version
            userNode.put("processedTimestamp", System.currentTimeMillis());
            userNode.put("entityVersion", ENTITY_VERSION);
            logger.info("User entity enriched with additional data: {}", userNode);
            return userNode;
        });
    }

    private CompletableFuture<ObjectNode> processValidateUser(ObjectNode userNode) {
        return CompletableFuture.supplyAsync(() -> {
            // Basic validation example: check required fields
            if (!userNode.hasNonNull("id") || userNode.get("id").asInt() <= 0) {
                logger.error("Validation failed: user id missing or invalid");
                userNode.put("validationError", "User id is missing or invalid");
            } else {
                logger.info("User entity validated successfully");
                userNode.remove("validationError");
            }
            return userNode;
        });
    }

    private CompletableFuture<ObjectNode> processFinalizeUser(ObjectNode userNode) {
        return CompletableFuture.supplyAsync(() -> {
            // Final processing step - e.g., mark entity as ready
            if (!userNode.has("validationError")) {
                userNode.put("status", "ready");
                logger.info("User entity marked as ready: {}", userNode);
            } else {
                userNode.put("status", "error");
                logger.info("User entity marked as error due to validation failure: {}", userNode);
            }
            return userNode;
        });
    }
}