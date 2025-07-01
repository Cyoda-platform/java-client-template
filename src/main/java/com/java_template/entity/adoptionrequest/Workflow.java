package com.java_template.entity.adoptionrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("adoptionrequest")
public class Workflow {

    // Placeholder for entityService, assuming it is set/injected elsewhere
    private EntityService entityService;

    @PostConstruct
    private void init() {
        // TODO: inject or assign entityService here if needed
    }

    public CompletableFuture<ObjectNode> processadoptionrequest(ObjectNode entity) {
        // Workflow orchestration only: invoke steps sequentially asynchronously
        return validatePetTechnicalId(entity)
                .thenCompose(valid -> updateAdoptionStatus(entity))
                .thenCompose(updated -> updatePetStatus(entity))
                .exceptionally(ex -> {
                    logger.error("Error in adoption request workflow", ex);
                    return entity;
                });
    }

    private CompletableFuture<Boolean> validatePetTechnicalId(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.hasNonNull("petTechnicalId")) {
                logger.warn("AdoptionRequest entity missing petTechnicalId");
                return false;
            }
            try {
                UUID.fromString(entity.get("petTechnicalId").asText());
                return true;
            } catch (Exception ex) {
                logger.error("Invalid petTechnicalId in adoption request: {}", entity.get("petTechnicalId").asText(), ex);
                return false;
            }
        });
    }

    private CompletableFuture<ObjectNode> updateAdoptionStatus(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(3000L); // Simulate processing delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted during adoption request processing", e);
                return entity;
            }
            entity.put("status", "approved");
            entity.put("message", "Your adoption request has been approved! Thank you.");
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> updatePetStatus(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.hasNonNull("petTechnicalId")) {
                return entity;
            }
            UUID petTechnicalId;
            try {
                petTechnicalId = UUID.fromString(entity.get("petTechnicalId").asText());
            } catch (Exception ex) {
                logger.error("Invalid petTechnicalId in adoption request: {}", entity.get("petTechnicalId").asText(), ex);
                return entity;
            }
            try {
                ObjectNode petNode = entityService.getItem("pet", ENTITY_VERSION, petTechnicalId).join();
                if (petNode != null) {
                    petNode.put("status", "sold");
                    // Cannot call updateItem on current entity, but can update pet entity
                    entityService.updateItem("pet", ENTITY_VERSION, petTechnicalId, petNode).join();
                    logger.info("Pet {} status updated to sold due to adoption approval", petTechnicalId);
                } else {
                    logger.warn("Pet entity not found for petTechnicalId {}", petTechnicalId);
                }
            } catch (Exception e) {
                logger.error("Error updating pet status during adoption approval", e);
            }
            return entity;
        });
    }

}