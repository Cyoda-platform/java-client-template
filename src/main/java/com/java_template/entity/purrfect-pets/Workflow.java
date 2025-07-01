package com.java_template.entity.purrfect_pets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("purrfect-pets")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    // Condition function: entity is NOT valid if not ObjectNode or missing name/type
    public CompletableFuture<Boolean> isNotValidEntity(ObjectNode entity) {
        boolean valid = entity != null
                && entity.isObject()
                && entity.hasNonNull("name")
                && entity.hasNonNull("type");
        boolean result = !valid;
        logger.info("isNotValidEntity: {}", result);
        return CompletableFuture.completedFuture(result);
    }

    // Condition function: entity is valid if ObjectNode and has name/type
    public CompletableFuture<Boolean> isValidEntity(ObjectNode entity) {
        boolean result = entity != null
                && entity.isObject()
                && entity.hasNonNull("name")
                && entity.hasNonNull("type");
        logger.info("isValidEntity: {}", result);
        return CompletableFuture.completedFuture(result);
    }

    // Action function: enrich entity fields: createdAt, normalized name, ageCategory, categoryDescription
    public CompletableFuture<ObjectNode> enrichEntity(ObjectNode entity) {
        logger.info("Enriching entity before persistence");

        if (!entity.has("createdAt")) {
            entity.put("createdAt", System.currentTimeMillis());
        }

        if (entity.has("name")) {
            String name = entity.get("name").asText();
            if (!name.isEmpty()) {
                String normalized = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
                entity.put("name", normalized);
            }
        }

        if (entity.has("age")) {
            int age = entity.get("age").asInt(0);
            String category = age < 1 ? "baby" : (age < 7 ? "adult" : "senior");
            entity.put("ageCategory", category);
        }

        try {
            String petType = entity.has("type") ? entity.get("type").asText() : null;
            if (petType != null && !petType.isEmpty()) {
                Optional<ObjectNode> categoryEntityOpt = entityService.getItem("pet-categories", ENTITY_VERSION, petType).get();
                if (categoryEntityOpt.isPresent()) {
                    ObjectNode cat = categoryEntityOpt.get();
                    if (cat.has("description")) {
                        entity.put("categoryDescription", cat.get("description").asText());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch pet category info", e);
        }

        return CompletableFuture.completedFuture(entity);
    }

    // Action function: async notification task (fire and forget)
    public CompletableFuture<ObjectNode> notifyExternalSystemAsync(ObjectNode entity) {
        logger.info("Dispatching async notification for entity");
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("[Async] Notifying external system about new pet: {}", entity.get("name").asText());
                Thread.sleep(100);
            } catch (Exception e) {
                logger.error("[Async] Failed to notify external system", e);
            }
        });
        return CompletableFuture.completedFuture(entity);
    }
}