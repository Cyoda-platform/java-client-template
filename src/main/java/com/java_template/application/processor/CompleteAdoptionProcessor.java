package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CompleteAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CompleteAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();

        // Business logic:
        // - Find Pet referenced by adoption request (entity.getPetId())
        // - Update Pet.status = "adopted" (if not already)
        // - Update the AdoptionRequest.status = "completed"
        // Notes: Do not perform update on AdoptionRequest via entityService. Changes to this entity are persisted by Cyoda automatically.
        if (entity == null) {
            logger.warn("AdoptionRequest entity is null in execution context");
            return entity;
        }

        String petRef = entity.getPetId();
        if (petRef == null || petRef.isBlank()) {
            logger.warn("AdoptionRequest {} has no petId, skipping pet update", entity.getId());
            // Still mark the request as completed per workflow semantics? We'll only mark completed if we can update the pet.
            return entity;
        }

        ObjectNode petItemNode = null;
        String petTechnicalIdStr = null;

        // Try direct fetch by UUID technical id
        try {
            UUID petUuid = UUID.fromString(petRef);
            CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                petUuid
            );
            petItemNode = petFuture.join();
            // If getItem returns a wrapper with 'entity' and 'technicalId', we keep the wrapper.
            if (petItemNode != null && petItemNode.has("technicalId")) {
                petTechnicalIdStr = petItemNode.has("technicalId") ? petItemNode.get("technicalId").asText() : null;
            } else if (petItemNode != null && petItemNode.has("entity")) {
                // try to extract possible technicalId
                petTechnicalIdStr = petItemNode.has("technicalId") ? petItemNode.get("technicalId").asText() : null;
            }
        } catch (IllegalArgumentException iae) {
            // petRef is not a UUID - fallback to search by business id
            logger.info("petId '{}' is not a UUID; falling back to search by business id", petRef);
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.id", "EQUALS", petRef)
            );
            try {
                CompletableFuture<ArrayNode> listFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    condition,
                    true
                );
                ArrayNode results = listFuture.join();
                if (results != null && results.size() > 0) {
                    petItemNode = (ObjectNode) results.get(0);
                    petTechnicalIdStr = petItemNode.has("technicalId") ? petItemNode.get("technicalId").asText() : null;
                }
            } catch (Exception ex) {
                logger.error("Error searching for Pet by business id {}: {}", petRef, ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Error fetching Pet {}: {}", petRef, ex.getMessage());
        }

        if (petItemNode == null) {
            logger.warn("Pet referenced by AdoptionRequest {} not found (petId={}), skipping pet update", entity.getId(), petRef);
            return entity;
        }

        // Extract the actual entity payload if wrapped
        ObjectNode petEntityNode = petItemNode.has("entity") ? (ObjectNode) petItemNode.get("entity") : petItemNode;
        Pet pet = null;
        try {
            pet = objectMapper.treeToValue(petEntityNode, Pet.class);
        } catch (Exception ex) {
            logger.error("Failed to deserialize Pet entity for petId {}: {}", petRef, ex.getMessage());
            return entity;
        }

        if (pet == null) {
            logger.warn("Deserialized Pet is null for petId {}", petRef);
            return entity;
        }

        // If pet already adopted, nothing to update; still mark request as completed
        boolean petAlreadyAdopted = pet.getStatus() != null && "adopted".equalsIgnoreCase(pet.getStatus());
        if (!petAlreadyAdopted) {
            pet.setStatus("adopted");
            try {
                // Determine technical id for update. If we already have a technicalId from wrapper, use it; otherwise try petRef as UUID.
                UUID technicalIdForUpdate = null;
                if (petTechnicalIdStr != null && !petTechnicalIdStr.isBlank()) {
                    technicalIdForUpdate = UUID.fromString(petTechnicalIdStr);
                } else {
                    // try petRef as UUID
                    try {
                        technicalIdForUpdate = UUID.fromString(petRef);
                    } catch (IllegalArgumentException iae) {
                        logger.warn("Cannot determine technicalId to update Pet (petRef not a UUID and wrapper missing technicalId). Pet will not be updated.");
                    }
                }

                if (technicalIdForUpdate != null) {
                    CompletableFuture<UUID> updateFuture = entityService.updateItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        technicalIdForUpdate,
                        pet
                    );
                    updateFuture.join();
                    logger.info("Updated Pet {} status to 'adopted' (technicalId={})", petRef, technicalIdForUpdate);
                } else {
                    logger.warn("Skipping Pet update because technicalId could not be determined for petRef={}", petRef);
                }
            } catch (Exception ex) {
                logger.error("Failed to update Pet {}: {}", petRef, ex.getMessage());
            }
        } else {
            logger.info("Pet {} already in 'adopted' status", petRef);
        }

        // Update the adoption request status to completed. This entity is persisted automatically by Cyoda.
        entity.setStatus("completed");
        logger.info("AdoptionRequest {} marked as 'completed'", entity.getId());

        return entity;
    }
}