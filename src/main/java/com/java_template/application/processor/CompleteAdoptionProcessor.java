package com.java_template.application.processor;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Business intent:
        // - Only complete adoption when request was previously APPROVED.
        // - Mark adoption request status to "completed" and set decisionDate if missing.
        // - Update Pet status to "adopted".
        // - Add pet id to Owner.favorites if not already present (as simple post-adoption linkage).
        try {
            String currentStatus = entity.getStatus();
            if (currentStatus == null || !currentStatus.equalsIgnoreCase("approved")) {
                logger.info("AdoptionRequest {} is not in APPROVED state (current={}). Skipping completion.", entity.getId(), currentStatus);
                return entity;
            }

            // Mark request as completed and ensure decisionDate is set
            entity.setStatus("completed");
            if (entity.getDecisionDate() == null || entity.getDecisionDate().isBlank()) {
                entity.setDecisionDate(Instant.now().toString());
            }

            // Update Pet status to 'adopted' if possible
            if (entity.getPetId() != null && !entity.getPetId().isBlank()) {
                try {
                    UUID petUuid = UUID.fromString(entity.getPetId());
                    CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        petUuid
                    );
                    ObjectNode petNode = petFuture.get(5, TimeUnit.SECONDS);
                    if (petNode != null && !petNode.isEmpty(null)) {
                        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
                        if (pet != null) {
                            pet.setStatus("adopted");
                            // perform update on the Pet entity (allowed - not the triggering entity)
                            try {
                                CompletableFuture<UUID> updated = entityService.updateItem(
                                    Pet.ENTITY_NAME,
                                    String.valueOf(Pet.ENTITY_VERSION),
                                    petUuid,
                                    pet
                                );
                                updated.get(5, TimeUnit.SECONDS);
                                logger.info("Pet {} marked as adopted for AdoptionRequest {}", pet.getId(), entity.getId());
                            } catch (Exception e) {
                                logger.warn("Failed to update Pet {} status to adopted: {}", petUuid, e.getMessage());
                            }
                        }
                    } else {
                        logger.warn("Pet with id {} not found when completing AdoptionRequest {}", entity.getPetId(), entity.getId());
                    }
                } catch (IllegalArgumentException iae) {
                    // petId is not a UUID string - log and skip updating Pet
                    logger.warn("PetId '{}' is not a valid UUID, cannot update Pet entity for AdoptionRequest {}.", entity.getPetId(), entity.getId());
                }
            } else {
                logger.warn("AdoptionRequest {} has no petId; skipping pet update.", entity.getId());
            }

            // Update Owner: add petId to favorites (non-destructive addition)
            if (entity.getOwnerId() != null && !entity.getOwnerId().isBlank() && entity.getPetId() != null && !entity.getPetId().isBlank()) {
                try {
                    UUID ownerUuid = UUID.fromString(entity.getOwnerId());
                    CompletableFuture<ObjectNode> ownerFuture = entityService.getItem(
                        Owner.ENTITY_NAME,
                        String.valueOf(Owner.ENTITY_VERSION),
                        ownerUuid
                    );
                    ObjectNode ownerNode = ownerFuture.get(5, TimeUnit.SECONDS);
                    if (ownerNode != null && !ownerNode.isEmpty(null)) {
                        Owner owner = objectMapper.treeToValue(ownerNode, Owner.class);
                        if (owner != null) {
                            List<String> favs = owner.getFavorites();
                            if (favs == null) {
                                favs = new ArrayList<>();
                            }
                            if (!favs.contains(entity.getPetId())) {
                                favs.add(entity.getPetId());
                                owner.setFavorites(favs);
                                try {
                                    CompletableFuture<UUID> updatedOwner = entityService.updateItem(
                                        Owner.ENTITY_NAME,
                                        String.valueOf(Owner.ENTITY_VERSION),
                                        ownerUuid,
                                        owner
                                    );
                                    updatedOwner.get(5, TimeUnit.SECONDS);
                                    logger.info("Owner {} favorites updated with pet {} after completion of AdoptionRequest {}", owner.getId(), entity.getPetId(), entity.getId());
                                } catch (Exception e) {
                                    logger.warn("Failed to update Owner {} after adoption completion: {}", ownerUuid, e.getMessage());
                                }
                            } else {
                                logger.debug("Owner {} already has pet {} in favorites.", owner.getId(), entity.getPetId());
                            }
                        }
                    } else {
                        logger.warn("Owner with id {} not found when completing AdoptionRequest {}", entity.getOwnerId(), entity.getId());
                    }
                } catch (IllegalArgumentException iae) {
                    logger.warn("OwnerId '{}' is not a valid UUID, cannot update Owner entity for AdoptionRequest {}.", entity.getOwnerId(), entity.getId());
                }
            }

        } catch (Exception e) {
            // Protect against unexpected exceptions so processor doesn't fail catastrophically.
            logger.error("Unexpected error while completing AdoptionRequest {}: {}", entity.getId(), e.getMessage(), e);
        }

        return entity;
    }
}