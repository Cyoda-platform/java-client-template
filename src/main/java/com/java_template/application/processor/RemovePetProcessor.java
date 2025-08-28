package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class RemovePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RemovePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RemovePetProcessor(SerializerFactory serializerFactory,
                              EntityService entityService,
                              ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        // If already removed, no-op
        String currentStatus = entity.getStatus();
        if (currentStatus != null && "REMOVED".equalsIgnoreCase(currentStatus)) {
            logger.info("Pet {} already REMOVED. No further action.", entity.getTechnicalId());
            return entity;
        }

        // Mark the pet as removed
        entity.setStatus("REMOVED");
        logger.info("Marked pet (technicalId={}, id={}) as REMOVED", entity.getTechnicalId(), entity.getId());

        // Business rule: Remove references to this pet from Owner.savedPets and Owner.adoptedPets
        try {
            CompletableFuture<List<DataPayload>> ownersFuture = entityService.getItems(
                Owner.ENTITY_NAME,
                Owner.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> ownerPayloads = ownersFuture.get();
            if (ownerPayloads != null) {
                for (DataPayload payload : ownerPayloads) {
                    try {
                        // Convert payload data to Owner
                        Owner owner = objectMapper.treeToValue(payload.getData(), Owner.class);
                        boolean modified = false;

                        if (owner.getSavedPets() != null) {
                            boolean removed = owner.getSavedPets().removeIf(petRef ->
                                (entity.getTechnicalId() != null && entity.getTechnicalId().equals(petRef)) ||
                                (entity.getId() != null && entity.getId().equals(petRef))
                            );
                            if (removed) {
                                modified = true;
                                logger.info("Removed pet reference from owner.savedPets (ownerId={}, pet={})", owner.getOwnerId(), entity.getTechnicalId() != null ? entity.getTechnicalId() : entity.getId());
                            }
                        }

                        if (owner.getAdoptedPets() != null) {
                            boolean removed = owner.getAdoptedPets().removeIf(petRef ->
                                (entity.getTechnicalId() != null && entity.getTechnicalId().equals(petRef)) ||
                                (entity.getId() != null && entity.getId().equals(petRef))
                            );
                            if (removed) {
                                modified = true;
                                logger.info("Removed pet reference from owner.adoptedPets (ownerId={}, pet={})", owner.getOwnerId(), entity.getTechnicalId() != null ? entity.getTechnicalId() : entity.getId());
                            }
                        }

                        if (modified) {
                            // Attempt to obtain technical id for the owner from payload metadata
                            String ownerTechnicalId = null;
                            try {
                                Object idObj = payload.getId();
                                if (idObj != null) {
                                    ownerTechnicalId = idObj.toString();
                                }
                            } catch (Exception ex) {
                                logger.warn("Unable to read technical id from owner payload; skipping update for owner with ownerId={}", owner.getOwnerId());
                            }

                            if (ownerTechnicalId != null && !ownerTechnicalId.isBlank()) {
                                try {
                                    CompletableFuture<UUID> updateFuture = entityService.updateItem(UUID.fromString(ownerTechnicalId), owner);
                                    updateFuture.get();
                                    logger.info("Updated owner entity after removing pet references (technicalId={})", ownerTechnicalId);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    logger.error("Interrupted while updating owner (technicalId={})", ownerTechnicalId, ie);
                                } catch (ExecutionException ee) {
                                    logger.error("Failed to update owner (technicalId={})", ownerTechnicalId, ee);
                                } catch (IllegalArgumentException iae) {
                                    logger.warn("Owner technical id is not a valid UUID (value={}); skipped update", ownerTechnicalId);
                                }
                            } else {
                                logger.warn("Owner payload missing technical id; cannot update persisted owner for ownerId={}", owner.getOwnerId());
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Failed processing owner payload while removing pet references: {}", e.getMessage(), e);
                        // continue processing other owners
                    }
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching owners for RemovePetProcessor", ie);
        } catch (ExecutionException ee) {
            logger.error("Execution exception while fetching owners for RemovePetProcessor", ee);
        } catch (Exception e) {
            logger.error("Unexpected error while processing owners in RemovePetProcessor", e);
        }

        // Return the modified pet entity; Cyoda will persist changes automatically for the current entity
        return entity;
    }
}