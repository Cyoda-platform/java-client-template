package com.java_template.entity.Category;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component("Category")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> validateCategory(ObjectNode entity) {
        logger.info("Starting validation for Category entity with id: {}", entity.path("id").asText());
        boolean valid = true;
        if (!entity.hasNonNull("name") || entity.get("name").asText().trim().isEmpty()) {
            logger.error("Validation failed: 'name' attribute is missing or empty");
            valid = false;
        }
        entity.put("validationPassed", valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isValidationFailed(ObjectNode entity) {
        boolean failed = !entity.path("validationPassed").asBoolean(false);
        entity.put("validationFailed", failed);
        logger.info("Validation failed: {}", failed);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isValidationSuccessful(ObjectNode entity) {
        boolean success = entity.path("validationPassed").asBoolean(false);
        entity.put("validationSuccessful", success);
        logger.info("Validation successful: {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> handleValidationFailure(ObjectNode entity) {
        logger.error("Handling validation failure for Category entity with id: {}", entity.path("id").asText());
        entity.put("status", "validation_failed");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> onValidationSuccess(ObjectNode entity) {
        logger.info("Validation succeeded for Category entity with id: {}", entity.path("id").asText());
        entity.put("status", "validated");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> storeCategory(ObjectNode entity) {
        logger.info("Storing Category entity with id: {}", entity.path("id").asText());
        // Simulate store success
        entity.put("storePassed", true);
        entity.put("status", "stored");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isStoreFailed(ObjectNode entity) {
        boolean failed = !entity.path("storePassed").asBoolean(false);
        entity.put("storeFailed", failed);
        logger.info("Store failed: {}", failed);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isStoreSuccessful(ObjectNode entity) {
        boolean success = entity.path("storePassed").asBoolean(false);
        entity.put("storeSuccessful", success);
        logger.info("Store successful: {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> handleStoreFailure(ObjectNode entity) {
        logger.error("Handling store failure for Category entity with id: {}", entity.path("id").asText());
        entity.put("status", "store_failed");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> cacheCategory(ObjectNode entity) {
        logger.info("Caching Category entity with id: {}", entity.path("id").asText());
        entity.put("cached", true);
        entity.put("status", "cached");
        return CompletableFuture.completedFuture(entity);
    }

}