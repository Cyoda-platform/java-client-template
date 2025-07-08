package com.java_template.entity.User;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component("User")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> handleValidationError(ObjectNode entity) {
        logger.error("Validation failed for user entity: {}", entity);
        entity.put("validationError", true);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> validateUserData(ObjectNode entity) {
        boolean valid = true;

        if (!entity.hasNonNull("username") || entity.get("username").asText().isEmpty()) {
            logger.error("Validation failed: username is missing or empty");
            valid = false;
        }
        if (!entity.hasNonNull("email") || entity.get("email").asText().isEmpty()) {
            logger.error("Validation failed: email is missing or empty");
            valid = false;
        }
        if (!entity.hasNonNull("password") || entity.get("password").asText().isEmpty()) {
            logger.error("Validation failed: password is missing or empty");
            valid = false;
        }
        if (!valid) {
            entity.put("validationError", true);
        } else {
            entity.put("validationError", false);
        }

        logger.info("Validation result for user {}: {}", entity.has("username") ? entity.get("username").asText() : "unknown", valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> hashUserPassword(ObjectNode entity) {
        if (entity.hasNonNull("password")) {
            String plainPassword = entity.get("password").asText();
            // Simple hash simulation (do NOT use in production)
            String hashedPassword = Integer.toHexString(plainPassword.hashCode());
            entity.put("password", hashedPassword);
            logger.info("Password hashed for user {}", entity.has("username") ? entity.get("username").asText() : "unknown");
        } else {
            logger.error("No password field found for hashing");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> storeUserData(ObjectNode entity) {
        // Store logic is handled outside this method, modify entity if needed
        entity.put("stored", true);
        logger.info("User data marked as stored for user {}", entity.has("username") ? entity.get("username").asText() : "unknown");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> authenticateUser(ObjectNode entity) {
        // Simple authentication simulation: if password is hashed and username present, authenticate success
        boolean authenticated = false;
        if (entity.hasNonNull("username") && entity.hasNonNull("password")) {
            String password = entity.get("password").asText();
            if (!password.isEmpty() && password.length() == 8 /* length of hexString hash */) {
                authenticated = true;
            }
        }
        entity.put("authenticated", authenticated);
        logger.info("Authentication {} for user {}", authenticated ? "succeeded" : "failed", entity.has("username") ? entity.get("username").asText() : "unknown");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> notifyUser(ObjectNode entity) {
        if (entity.hasNonNull("email")) {
            String email = entity.get("email").asText();
            // Simulate notification sending
            logger.info("Notification sent to user email: {}", email);
            entity.put("notified", true);
        } else {
            logger.error("No email found to notify user");
            entity.put("notified", false);
        }
        return CompletableFuture.completedFuture(entity);
    }
}