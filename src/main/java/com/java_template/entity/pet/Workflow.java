package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("pet")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    public CompletableFuture<ObjectNode> handleValidationError(ObjectNode entity) {
        logger.error("Validation failed for pet entity with id: {}", entity.path("id").asText("unknown"));
        entity.put("validationError", true);
        entity.put("errorMessage", "Validation failed: missing required fields or invalid data.");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> validatePetData(ObjectNode entity) {
        logger.info("Starting validation for pet entity with id: {}", entity.path("id").asText("unknown"));
        boolean valid = true;
        if (!entity.hasNonNull("name") || entity.get("name").asText().isEmpty()) {
            logger.error("Validation error: missing or empty 'name'");
            valid = false;
        }
        if (!entity.hasNonNull("category") || entity.get("category").isNull() || entity.get("category").isMissingNode()) {
            logger.error("Validation error: missing 'category'");
            valid = false;
        } else {
            // Validate category object or id presence
            if (!entity.get("category").hasNonNull("id") && !entity.get("category").hasNonNull("name")) {
                logger.error("Validation error: category missing 'id' and 'name'");
                valid = false;
            }
        }
        if (!entity.hasNonNull("status")) {
            logger.error("Validation error: missing 'status'");
            valid = false;
        } else {
            String status = entity.get("status").asText();
            if (!(status.equals("available") || status.equals("pending") || status.equals("sold"))) {
                logger.error("Validation error: invalid 'status' value: {}", status);
                valid = false;
            }
        }
        entity.put("validationPassed", valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> enrichPetData(ObjectNode entity) {
        logger.info("Enriching pet entity with id: {}", entity.path("id").asText("unknown"));
        // Example enrichment: if category is only id, add default name (in real app, would query DB)
        if (entity.has("category") && entity.get("category").hasNonNull("id")) {
            int categoryId = entity.get("category").get("id").asInt(-1);
            if (!entity.get("category").hasNonNull("name") || entity.get("category").get("name").asText().isEmpty()) {
                String categoryName = resolveCategoryNameById(categoryId);
                ((com.fasterxml.jackson.databind.node.ObjectNode) entity.get("category")).put("name", categoryName);
                logger.info("Category name enriched to '{}'", categoryName);
            }
        }
        // Tags enrichment: if tags are ids only, add dummy tag names (real app would query DB)
        if (entity.has("tags") && entity.get("tags").isArray()) {
            for (int i = 0; i < entity.get("tags").size(); i++) {
                ObjectNode tag = (ObjectNode) entity.get("tags").get(i);
                if (tag.hasNonNull("id") && (!tag.hasNonNull("name") || tag.get("name").asText().isEmpty())) {
                    int tagId = tag.get("id").asInt(-1);
                    String tagName = resolveTagNameById(tagId);
                    tag.put("name", tagName);
                    logger.info("Tag id {} enriched with name '{}'", tagId, tagName);
                }
            }
        }
        return CompletableFuture.completedFuture(entity);
    }

    private String resolveCategoryNameById(int id) {
        // Dummy mapping - in real scenario, query DB or cache
        switch (id) {
            case 1: return "Dog";
            case 2: return "Cat";
            case 3: return "Bird";
            default: return "Unknown";
        }
    }

    private String resolveTagNameById(int id) {
        // Dummy mapping
        switch (id) {
            case 1: return "playful";
            case 2: return "cute";
            case 3: return "small";
            default: return "general";
        }
    }

    public CompletableFuture<ObjectNode> storePetData(ObjectNode entity) {
        logger.info("Storing pet entity with id: {}", entity.path("id").asText("unknown"));
        // Simulate store by marking stored = true
        entity.put("stored", true);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> notifyUsers(ObjectNode entity) {
        logger.info("Notifying users about pet entity with id: {}", entity.path("id").asText("unknown"));
        // Simulate notification sent
        entity.put("notified", true);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> updatePetCache(ObjectNode entity) {
        logger.info("Updating cache for pet entity with id: {}", entity.path("id").asText("unknown"));
        // Simulate cache update
        entity.put("cacheUpdated", true);
        return CompletableFuture.completedFuture(entity);
    }
}