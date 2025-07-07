package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component("pet")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    // Condition: Check if createdAt is missing
    public boolean isCreatedAtMissing(ObjectNode petNode) {
        boolean missing = !petNode.has("createdAt");
        logger.info("Condition isCreatedAtMissing: {}", missing);
        return missing;
    }

    // Action: Add createdAt if missing
    public CompletableFuture<ObjectNode> addCreatedAtIfMissing(ObjectNode petNode) {
        if (!petNode.has("createdAt")) {
            petNode.put("createdAt", System.currentTimeMillis());
            logger.info("Action addCreatedAtIfMissing applied");
        }
        return CompletableFuture.completedFuture(petNode);
    }

    // Condition: Check if name is not normalized (not capitalized first letter)
    public boolean isNameNotNormalized(ObjectNode petNode) {
        if (!petNode.hasNonNull("name")) return false;
        String name = petNode.get("name").asText().trim();
        if (name.isEmpty()) return false;
        boolean notNormalized = !Character.isUpperCase(name.charAt(0));
        logger.info("Condition isNameNotNormalized: {}", notNormalized);
        return notNormalized;
    }

    // Action: Normalize name by trimming and capitalizing first letter
    public CompletableFuture<ObjectNode> normalizeName(ObjectNode petNode) {
        if (petNode.hasNonNull("name")) {
            String name = petNode.get("name").asText().trim();
            if (!name.isEmpty()) {
                String normalized = name.substring(0, 1).toUpperCase() + name.substring(1);
                petNode.put("name", normalized);
                logger.info("Action normalizeName applied");
            }
        }
        return CompletableFuture.completedFuture(petNode);
    }

    // Action: Enrich with default tags asynchronously from "tag" entity
    public CompletableFuture<ObjectNode> enrichWithDefaultTags(ObjectNode petNode) {
        ArrayNode petTagsNode;
        if (petNode.has("tags") && petNode.get("tags").isArray()) {
            petTagsNode = (ArrayNode) petNode.get("tags");
        } else {
            petTagsNode = petNode.objectNode().arrayNode();
            petNode.set("tags", petTagsNode);
        }
        Set<String> currentTags = new HashSet<>();
        petTagsNode.forEach(t -> currentTags.add(t.asText()));

        CompletableFuture<ArrayNode> defaultTagsFuture = entityService.getItemsByCondition(
                "tag", ENTITY_VERSION,
                com.java_template.common.dto.SearchConditionRequest.group("AND",
                        com.java_template.common.dto.Condition.of("$.isDefault", "EQUALS", true))
        );

        return defaultTagsFuture.thenApply(defaultTags -> {
            for (JsonNode tagNode : defaultTags) {
                if (tagNode.has("name")) {
                    String tagName = tagNode.get("name").asText();
                    if (!currentTags.contains(tagName)) {
                        petTagsNode.add(tagName);
                        currentTags.add(tagName);
                    }
                }
            }
            logger.info("Action enrichWithDefaultTags applied");
            return petNode;
        });
    }

    // Condition: Check if status is "pending"
    public boolean isStatusPending(ObjectNode petNode) {
        boolean pending = petNode.hasNonNull("status") && "pending".equalsIgnoreCase(petNode.get("status").asText());
        logger.info("Condition isStatusPending: {}", pending);
        return pending;
    }

    // Action: Add statusReason if status is pending, else remove statusReason
    public CompletableFuture<ObjectNode> enrichStatusReason(ObjectNode petNode) {
        if (petNode.hasNonNull("status") && "pending".equalsIgnoreCase(petNode.get("status").asText())) {
            petNode.put("statusReason", "Awaiting approval");
            logger.info("Action enrichStatusReason applied: added statusReason");
        } else {
            petNode.remove("statusReason");
            logger.info("Action enrichStatusReason applied: removed statusReason");
        }
        return CompletableFuture.completedFuture(petNode);
    }
}