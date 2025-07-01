package com.java_template.entity.pet_favorites;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Component("pet-favorites")
public class Workflow {

    private final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    // Condition function: check required fields userId and petId are present and non-empty
    public CompletableFuture<Boolean> hasRequiredFields(ObjectNode entity) {
        boolean valid = entity.hasNonNull("userId") && !entity.get("userId").asText().isEmpty()
                && entity.hasNonNull("petId") && !entity.get("petId").asText().isEmpty();
        logger.info("hasRequiredFields check result: {}", valid);
        return CompletableFuture.completedFuture(valid);
    }

    // Condition function: negate hasRequiredFields
    public CompletableFuture<Boolean> negateHasRequiredFields(ObjectNode entity) {
        boolean invalid = !(entity.hasNonNull("userId") && !entity.get("userId").asText().isEmpty()
                && entity.hasNonNull("petId") && !entity.get("petId").asText().isEmpty());
        logger.info("negateHasRequiredFields check result: {}", invalid);
        return CompletableFuture.completedFuture(invalid);
    }

    // Action function: process pet favorites entity before persistence
    public CompletableFuture<ObjectNode> processpet_favorites(ObjectNode entity) {
        logger.info("Running workflow processpet_favorites before persistence");

        if (!entity.isObject()) {
            logger.warn("Entity is not an ObjectNode, cannot process");
            return CompletableFuture.completedFuture(entity);
        }

        // Add timestamp if missing
        if (!entity.has("addedAt")) {
            entity.put("addedAt", System.currentTimeMillis());
        }

        // Fire-and-forget async task to log favorite addition to external monitoring system
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("[Async] User {} added favorite pet {}", entity.get("userId").asText(), entity.get("petId").asText());
                Thread.sleep(50); // simulate delay or external call
            } catch (Exception e) {
                logger.error("[Async] Failed to log favorite addition", e);
            }
        });

        return CompletableFuture.completedFuture(entity);
    }

    // Action function: placeholder for persisting favorite entity (modify entity if needed)
    public CompletableFuture<ObjectNode> persistFavorite(ObjectNode entity) {
        logger.info("Persisting favorite entity for userId={} petId={}", entity.get("userId").asText(), entity.get("petId").asText());
        // TODO: Add any pre-persistence logic if needed. For prototype, just return entity.
        return CompletableFuture.completedFuture(entity);
    }
}