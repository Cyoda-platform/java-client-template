package com.java_template.entity.pets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Component("pets")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<Boolean> needsDescriptionFix(ObjectNode entity) {
        boolean needsFix = !entity.hasNonNull("description") || entity.get("description").asText().trim().isEmpty();
        logger.info("needsDescriptionFix: {}", needsFix);
        return CompletableFuture.completedFuture(needsFix);
    }

    public CompletableFuture<Boolean> negateNeedsDescriptionFix(ObjectNode entity) {
        return needsDescriptionFix(entity).thenApply(result -> !result);
    }

    public CompletableFuture<ObjectNode> fixDescription(ObjectNode entity) {
        logger.info("fixDescription: setting default description");
        entity.put("description", "No description available.");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<Boolean> needsStatusNormalization(ObjectNode entity) {
        if (!entity.hasNonNull("status")) {
            logger.info("needsStatusNormalization: status not present, no normalization needed");
            return CompletableFuture.completedFuture(false);
        }
        String status = entity.get("status").asText();
        boolean needsNormalization = !status.equals(status.toLowerCase(Locale.ROOT));
        logger.info("needsStatusNormalization: {}", needsNormalization);
        return CompletableFuture.completedFuture(needsNormalization);
    }

    public CompletableFuture<Boolean> negateNeedsStatusNormalization(ObjectNode entity) {
        return needsStatusNormalization(entity).thenApply(result -> !result);
    }

    public CompletableFuture<ObjectNode> normalizeStatus(ObjectNode entity) {
        if (entity.hasNonNull("status")) {
            String oldStatus = entity.get("status").asText();
            String newStatus = oldStatus.toLowerCase(Locale.ROOT);
            entity.put("status", newStatus);
            logger.info("normalizeStatus: changed status from '{}' to '{}'", oldStatus, newStatus);
        } else {
            logger.info("normalizeStatus: no status field to normalize");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> computeNameCategory(ObjectNode entity) {
        String name = entity.hasNonNull("name") ? entity.get("name").asText() : "";
        String category = entity.hasNonNull("category") ? entity.get("category").asText() : "";
        String nameCategory = name + "-" + category;
        entity.put("nameCategory", nameCategory);
        logger.info("computeNameCategory: set nameCategory to '{}'", nameCategory);
        return CompletableFuture.completedFuture(entity);
    }
}