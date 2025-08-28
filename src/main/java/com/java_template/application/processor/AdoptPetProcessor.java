package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AdoptPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdoptPetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        Pet pet = context.entity();

        try {
            // Determine the pet reference used by AdoptionRequest.petId (prefer external id, fallback to technicalId)
            String petRef = (pet.getId() != null && !pet.getId().isBlank()) ? pet.getId()
                    : (pet.getTechnicalId() != null && !pet.getTechnicalId().isBlank() ? pet.getTechnicalId() : null);

            if (petRef == null || petRef.isBlank()) {
                logger.warn("Pet reference (id or technicalId) is missing; cannot correlate adoption requests. Pet technicalId={}", pet.getTechnicalId());
                return pet;
            }

            // Find adoption requests that reference this pet
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.petId", "EQUALS", petRef)
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                AdoptionRequest.ENTITY_NAME,
                AdoptionRequest.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = filteredItemsFuture.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("No adoption requests found for petRef={}", petRef);
                return pet;
            }

            // Collect approved adoption requests to complete adoption
            List<DataPayload> approvedRequests = new ArrayList<>();
            for (DataPayload payload : dataPayloads) {
                try {
                    JsonNode dataNode = payload.getData();
                    if (dataNode == null) continue;
                    AdoptionRequest req = objectMapper.treeToValue(dataNode, AdoptionRequest.class);
                    if (req != null && req.getStatus() != null && req.getStatus().equalsIgnoreCase("approved")) {
                        approvedRequests.add(payload);
                    }
                } catch (Exception e) {
                    logger.error("Failed to deserialize AdoptionRequest payload for petRef={}: {}", petRef, e.getMessage(), e);
                }
            }

            if (approvedRequests.isEmpty()) {
                logger.info("No approved adoption requests found for petRef={}; adoption will not be completed.", petRef);
                return pet;
            }

            // At least one approved request exists -> complete adoption
            // Only change in-memory pet status; persistence is handled by workflow engine.
            pet.setStatus("ADOPTED");

            // For each approved request: mark COMPLETED and add pet to owner's adoptedPets
            for (DataPayload approvedPayload : approvedRequests) {
                try {
                    JsonNode reqNode = approvedPayload.getData();
                    if (reqNode == null) {
                        logger.warn("Approved AdoptionRequest payload has no data; skipping.");
                        continue;
                    }
                    AdoptionRequest req = objectMapper.treeToValue(reqNode, AdoptionRequest.class);
                    if (req == null) continue;

                    // Update adoption request status to COMPLETED and set decisionAt if not present
                    req.setStatus("COMPLETED");
                    if (req.getDecisionAt() == null || req.getDecisionAt().isBlank()) {
                        req.setDecisionAt(Instant.now().toString());
                    }

                    // Persist updated AdoptionRequest using its technical id from payload data if present
                    try {
                        String reqTechnicalId = null;
                        if (reqNode.has("technicalId") && !reqNode.get("technicalId").isNull()) {
                            reqTechnicalId = reqNode.get("technicalId").asText();
                        } else if (reqNode.has("id") && !reqNode.get("id").isNull()) {
                            reqTechnicalId = reqNode.get("id").asText();
                        }

                        if (reqTechnicalId != null && !reqTechnicalId.isBlank()) {
                            try {
                                entityService.updateItem(UUID.fromString(reqTechnicalId), req).get();
                            } catch (IllegalArgumentException iae) {
                                // technical id not a UUID; log and skip update
                                logger.warn("AdoptionRequest technical id is not a UUID ({}). Update skipped for requestId={}", reqTechnicalId, req.getRequestId());
                            }
                        } else {
                            logger.warn("No technical id available for AdoptionRequest; update skipped for requestId={}", req.getRequestId());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to update AdoptionRequest for requestId={}: {}", req.getRequestId(), e.getMessage(), e);
                    }

                    // Update Owner: find owner by ownerId == requesterId
                    String ownerBusinessId = req.getRequesterId();
                    if (ownerBusinessId == null || ownerBusinessId.isBlank()) {
                        logger.warn("AdoptionRequest requesterId is blank for requestId={}; cannot update Owner", req.getRequestId());
                        continue;
                    }

                    SearchConditionRequest ownerCondition = SearchConditionRequest.group(
                        "AND",
                        Condition.of("$.ownerId", "EQUALS", ownerBusinessId)
                    );

                    CompletableFuture<List<DataPayload>> ownerItemsFuture = entityService.getItemsByCondition(
                        Owner.ENTITY_NAME,
                        Owner.ENTITY_VERSION,
                        ownerCondition,
                        true
                    );

                    List<DataPayload> ownerPayloads = ownerItemsFuture.get();
                    if (ownerPayloads == null || ownerPayloads.isEmpty()) {
                        logger.warn("No Owner entity found for ownerId={}", ownerBusinessId);
                        continue;
                    }

                    for (DataPayload ownerPayload : ownerPayloads) {
                        try {
                            JsonNode ownerNode = ownerPayload.getData();
                            if (ownerNode == null) continue;
                            Owner owner = objectMapper.treeToValue(ownerNode, Owner.class);
                            if (owner == null) continue;

                            List<String> adopted = owner.getAdoptedPets();
                            if (adopted == null) {
                                adopted = new ArrayList<>();
                            }
                            // Use petRef as stored reference in owner.adoptedPets
                            if (!adopted.contains(petRef)) {
                                adopted.add(petRef);
                                owner.setAdoptedPets(adopted);
                            }

                            // Persist owner update using technical id from owner payload data if available
                            try {
                                String ownerTechnicalId = null;
                                if (ownerNode.has("technicalId") && !ownerNode.get("technicalId").isNull()) {
                                    ownerTechnicalId = ownerNode.get("technicalId").asText();
                                } else if (ownerNode.has("id") && !ownerNode.get("id").isNull()) {
                                    ownerTechnicalId = ownerNode.get("id").asText();
                                }

                                if (ownerTechnicalId != null && !ownerTechnicalId.isBlank()) {
                                    try {
                                        entityService.updateItem(UUID.fromString(ownerTechnicalId), owner).get();
                                    } catch (IllegalArgumentException iae) {
                                        logger.warn("Owner technical id is not a UUID ({}). Update skipped for ownerId={}", ownerTechnicalId, owner.getOwnerId());
                                    }
                                } else {
                                    logger.warn("No technical id available for Owner; update skipped for ownerId={}", owner.getOwnerId());
                                }
                            } catch (Exception e) {
                                logger.error("Failed to update Owner for ownerId={}: {}", owner.getOwnerId(), e.getMessage(), e);
                            }

                        } catch (Exception e) {
                            logger.error("Failed to deserialize Owner payload for ownerId={}: {}", ownerBusinessId, e.getMessage(), e);
                        }
                    }

                } catch (Exception e) {
                    logger.error("Failed processing approved AdoptionRequest payload for petRef={}: {}", petRef, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            logger.error("Error while completing adoption for pet technicalId={}: {}", pet.getTechnicalId(), e.getMessage(), e);
            // In case of unexpected error, do not change pet in-memory status to avoid inconsistent persisted state.
        }

        return pet;
    }
}