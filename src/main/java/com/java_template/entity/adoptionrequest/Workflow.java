package com.java_template.entity.adoptionrequest;

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

    private EntityService entityService;

    @PostConstruct
    private void init() {
        // TODO: inject or assign entityService here if needed
    }

    public CompletableFuture<ObjectNode> processadoptionrequest(ObjectNode entity) {
        return validatePetTechnicalId(entity)
                .thenCompose(valid -> {
                    if (!valid.hasNonNull("success") || !valid.get("success").asBoolean()) {
                        return CompletableFuture.completedFuture(valid);
                    }
                    return updateAdoptionStatus(entity);
                })
                .thenCompose(updatedStatus -> {
                    if (updatedStatus.hasNonNull("status") && "approved".equals(updatedStatus.get("status").asText())) {
                        return updatePetStatus(entity);
                    }
                    return CompletableFuture.completedFuture(updatedStatus);
                })
                .exceptionally(ex -> {
                    logger.error("Error in adoption request workflow", ex);
                    return entity;
                });
    }

    public CompletableFuture<ObjectNode> validatePetTechnicalId(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isValid = false;
            if (entity.hasNonNull("petTechnicalId")) {
                try {
                    UUID.fromString(entity.get("petTechnicalId").asText());
                    isValid = true;
                } catch (Exception ex) {
                    logger.error("Invalid petTechnicalId in adoption request: {}", entity.get("petTechnicalId").asText(), ex);
                }
            } else {
                logger.warn("AdoptionRequest entity missing petTechnicalId");
            }
            entity.put("success", isValid);
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> validatePetTechnicalId_returns_bool(ObjectNode entity) {
        boolean value = false;
        if (entity.hasNonNull("petTechnicalId")) {
            try {
                UUID.fromString(entity.get("petTechnicalId").asText());
                value = true;
            } catch (Exception ex) {
                value = false;
            }
        }
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> validatePetTechnicalId_returns_bool_negated(ObjectNode entity) {
        boolean value = true;
        if (entity.hasNonNull("petTechnicalId")) {
            try {
                UUID.fromString(entity.get("petTechnicalId").asText());
                value = false;
            } catch (Exception ex) {
                value = true;
            }
        } else {
            value = true;
        }
        entity.put("success", value);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> updateAdoptionStatus(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(3000L);
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

    public CompletableFuture<ObjectNode> updatePetStatus(ObjectNode entity) {
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