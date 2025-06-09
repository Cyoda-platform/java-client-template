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

    private static final Logger logger = LoggerFactory.getLogger(UserWorkflow.class);
    private final ObjectMapper objectMapper;

    public UserWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Workflow orchestration method - no business logic here
    public CompletableFuture<ObjectNode> processUser(ObjectNode entity) {
        return processFetchSupplementaryData(entity)
                .thenCompose(this::processAddCreationTimestamp)
                .thenCompose(this::processFinalize);
    }

    // Business logic: fetch supplementary data asynchronously and modify entity directly
    private CompletableFuture<ObjectNode> processFetchSupplementaryData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Fetching supplementary data for user: {}", entity.get("email").asText());
                entity.put("supplementaryField", "Sample value");
            } catch (Exception e) {
                logger.error("Error fetching supplementary data", e);
            }
            return entity;
        });
    }

    // Business logic: add creation timestamp
    private CompletableFuture<ObjectNode> processAddCreationTimestamp(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                entity.put("createdAt", System.currentTimeMillis());
                logger.info("Added creation timestamp to user entity: {}", entity);
            } catch (Exception e) {
                logger.error("Error adding creation timestamp", e);
            }
            return entity;
        });
    }

    // Business logic: finalize processing - placeholder for any final steps
    private CompletableFuture<ObjectNode> processFinalize(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Finalizing user processing for: {}", entity);
            } catch (Exception e) {
                logger.error("Error finalizing user processing", e);
            }
            return entity;
        });
    }
}