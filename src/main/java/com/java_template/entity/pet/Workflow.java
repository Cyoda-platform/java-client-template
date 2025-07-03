package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("pet")
public class Workflow {

    private final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    // Assumed injected or accessible entityService with getItems method
    private EntityService entityService;

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    // Validate and default status if missing or blank
    public CompletableFuture<ObjectNode> validateAndDefaultStatus(ObjectNode entity) {
        if (!entity.hasNonNull("status") || entity.get("status").asText().isBlank()) {
            entity.put("status", "available");
            logger.info("Status defaulted to 'available'");
        } else {
            logger.info("Status present: {}", entity.get("status").asText());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Normalize tags by trimming and removing duplicates
    public CompletableFuture<ObjectNode> normalizeTags(ObjectNode entity) {
        if (entity.has("tags") && entity.get("tags").isArray()) {
            Set<String> uniqueTags = new LinkedHashSet<>();
            entity.withArray("tags").forEach(tagNode -> {
                if (tagNode.isTextual()) {
                    uniqueTags.add(tagNode.asText().trim());
                }
            });
            var tagsArrayNode = entity.putArray("tags");
            uniqueTags.forEach(tagsArrayNode::add);
            logger.info("Tags normalized: {}", uniqueTags);
        } else {
            logger.info("No tags to normalize");
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Enrich categoryDescription by fetching category data and matching by name
    public CompletableFuture<ObjectNode> enrichCategory(ObjectNode entity) {
        if (entity.hasNonNull("category")) {
            String categoryName = entity.get("category").asText();
            try {
                CompletableFuture<ArrayNode> categoryFuture = entityService.getItems("category", ENTITY_VERSION);
                ArrayNode categories = categoryFuture.get(); // blocking here for prototype
                boolean enriched = false;
                for (JsonNode catNode : categories) {
                    if (categoryName.equalsIgnoreCase(catNode.path("name").asText())) {
                        entity.put("categoryDescription", catNode.path("description").asText(""));
                        enriched = true;
                        logger.info("Category enriched for '{}'", categoryName);
                        break;
                    }
                }
                if (!enriched) {
                    logger.info("No matching category found for '{}'", categoryName);
                }
            } catch (Exception e) {
                logger.warn("Category enrichment failed: {}", e.getMessage());
            }
        } else {
            logger.info("No category field present for enrichment");
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: needs status defaulting if missing or blank
    public boolean needsStatusDefault(ObjectNode entity) {
        boolean needs = !entity.hasNonNull("status") || entity.get("status").asText().isBlank();
        logger.info("Condition needsStatusDefault: {}", needs);
        return needs;
    }

    // Condition function: needs tag normalization if tags array exists and is non-empty
    public boolean needsTagNormalization(ObjectNode entity) {
        boolean needs = entity.has("tags") && entity.get("tags").isArray() && entity.get("tags").size() > 0;
        logger.info("Condition needsTagNormalization: {}", needs);
        return needs;
    }

    // Condition function: needs category enrichment if category exists and categoryDescription missing
    public boolean needsCategoryEnrichment(ObjectNode entity) {
        boolean needs = entity.hasNonNull("category") && !entity.hasNonNull("categoryDescription");
        logger.info("Condition needsCategoryEnrichment: {}", needs);
        return needs;
    }
}