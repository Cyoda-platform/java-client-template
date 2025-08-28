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

import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

        try {
            // Work with JSON tree to avoid compile-time reliance on generated getters/setters (Lombok)
            ObjectNode petNode = (ObjectNode) objectMapper.valueToTree(entity);
            String currentStatus = petNode.hasNonNull("status") ? petNode.get("status").asText() : null;
            if (currentStatus != null && "REMOVED".equalsIgnoreCase(currentStatus)) {
                String techIdLog = petNode.hasNonNull("technicalId") ? petNode.get("technicalId").asText() : null;
                String extIdLog = petNode.hasNonNull("id") ? petNode.get("id").asText() : null;
                logger.info("Pet {} already REMOVED. No further action.", techIdLog != null ? techIdLog : extIdLog);
                return entity;
            }

            // Mark the pet as removed in the returned entity structure
            petNode.put("status", "REMOVED");
            Pet modifiedPet = objectMapper.treeToValue(petNode, Pet.class);
            logger.info("Marked pet (technicalId={}, id={}) as REMOVED",
                petNode.hasNonNull("technicalId") ? petNode.get("technicalId").asText() : null,
                petNode.hasNonNull("id") ? petNode.get("id").asText() : null
            );

            // Prepare matching identifiers to remove from owners
            String petTechnicalId = petNode.hasNonNull("technicalId") ? petNode.get("technicalId").asText() : null;
            String petExternalId = petNode.hasNonNull("id") ? petNode.get("id").asText() : null;

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
                            if (payload == null || payload.getData() == null) continue;
                            ObjectNode ownerNode = (ObjectNode) payload.getData();
                            boolean modified = false;

                            // savedPets
                            if (ownerNode.has("savedPets") && ownerNode.get("savedPets").isArray()) {
                                ArrayNode saved = (ArrayNode) ownerNode.get("savedPets");
                                ArrayNode newSaved = objectMapper.createArrayNode();
                                for (int i = 0; i < saved.size(); i++) {
                                    String ref = saved.get(i).asText(null);
                                    if (ref == null) continue;
                                    boolean matches = (petTechnicalId != null && petTechnicalId.equals(ref)) ||
                                                      (petExternalId != null && petExternalId.equals(ref));
                                    if (!matches) {
                                        newSaved.add(ref);
                                    } else {
                                        modified = true;
                                        logger.info("Removed pet reference from owner.savedPets (ownerBusinessId={}, pet={})",
                                                ownerNode.hasNonNull("ownerId") ? ownerNode.get("ownerId").asText() : "unknown",
                                                petTechnicalId != null ? petTechnicalId : petExternalId);
                                    }
                                }
                                if (modified) {
                                    ownerNode.set("savedPets", newSaved);
                                }
                            }

                            // adoptedPets
                            if (ownerNode.has("adoptedPets") && ownerNode.get("adoptedPets").isArray()) {
                                ArrayNode adopted = (ArrayNode) ownerNode.get("adoptedPets");
                                ArrayNode newAdopted = objectMapper.createArrayNode();
                                boolean adoptedModified = false;
                                for (int i = 0; i < adopted.size(); i++) {
                                    String ref = adopted.get(i).asText(null);
                                    if (ref == null) continue;
                                    boolean matches = (petTechnicalId != null && petTechnicalId.equals(ref)) ||
                                                      (petExternalId != null && petExternalId.equals(ref));
                                    if (!matches) {
                                        newAdopted.add(ref);
                                    } else {
                                        adoptedModified = true;
                                        logger.info("Removed pet reference from owner.adoptedPets (ownerBusinessId={}, pet={})",
                                                ownerNode.hasNonNull("ownerId") ? ownerNode.get("ownerId").asText() : "unknown",
                                                petTechnicalId != null ? petTechnicalId : petExternalId);
                                    }
                                }
                                if (adoptedModified) {
                                    ownerNode.set("adoptedPets", newAdopted);
                                    modified = true;
                                }
                            }

                            if (modified) {
                                // Attempt to find a persisted technical id for the owner.
                                // Prefer a technicalId field in the payload data; fallback to 'id' if present.
                                String ownerTechnicalId = null;
                                if (ownerNode.hasNonNull("technicalId")) {
                                    ownerTechnicalId = ownerNode.get("technicalId").asText();
                                } else if (ownerNode.hasNonNull("id")) {
                                    ownerTechnicalId = ownerNode.get("id").asText();
                                } else {
                                    // If the persisted owner technical id is not present in the data node,
                                    // we cannot safely call updateItem because DataPayload API for metadata id
                                    // may not be available/consistent across runtimes. Log and skip.
                                    logger.warn("Owner payload missing technical id in data; cannot update persisted owner for ownerBusinessId={}",
                                        ownerNode.hasNonNull("ownerId") ? ownerNode.get("ownerId").asText() : "unknown");
                                    continue;
                                }

                                if (ownerTechnicalId != null && !ownerTechnicalId.isBlank()) {
                                    try {
                                        Owner ownerObj = objectMapper.treeToValue(ownerNode, Owner.class);
                                        CompletableFuture<UUID> updateFuture = entityService.updateItem(UUID.fromString(ownerTechnicalId), ownerObj);
                                        updateFuture.get();
                                        logger.info("Updated owner entity after removing pet references (technicalId={})", ownerTechnicalId);
                                    } catch (IllegalArgumentException iae) {
                                        logger.warn("Owner technical id is not a valid UUID (value={}); skipped update", ownerTechnicalId);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        logger.error("Interrupted while updating owner (technicalId={})", ownerTechnicalId, ie);
                                    } catch (ExecutionException ee) {
                                        logger.error("Failed to update owner (technicalId={})", ownerTechnicalId, ee);
                                    } catch (Exception ex) {
                                        logger.error("Unexpected error while updating owner for ownerTechnicalId={}: {}", ownerTechnicalId, ex.getMessage(), ex);
                                    }
                                } else {
                                    logger.warn("Owner payload missing technical id; cannot update persisted owner for ownerBusinessId={}",
                                        ownerNode.hasNonNull("ownerId") ? ownerNode.get("ownerId").asText() : "unknown");
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
            return modifiedPet;
        } catch (Exception ex) {
            logger.error("Unexpected error in RemovePetProcessor: {}", ex.getMessage(), ex);
            // If something goes wrong, return original entity (no-op)
            return entity;
        }
    }
}