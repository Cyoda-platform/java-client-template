package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
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

@Component
public class CompleteAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CompleteAdoptionProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
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
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
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
        AdoptionRequest request = context.entity();

        try {
            String petRef = request.getPetId();
            String requesterRef = request.getRequesterId();

            if (petRef == null || petRef.isBlank()) {
                logger.error("AdoptionRequest missing petId. requestId={}", request.getRequestId());
                request.setStatus("REJECTED");
                request.setDecisionAt(Instant.now().toString());
                return request;
            }

            // 1) Retrieve Pet - attempt by technicalId (UUID)
            Pet pet = null;
            DataPayload petPayload = null;
            try {
                CompletableFuture<DataPayload> petFuture = entityService.getItem(UUID.fromString(petRef));
                petPayload = petFuture.get();
            } catch (Exception ex) {
                // ignore here, will try fallback by searching for pet.id == petRef
                petPayload = null;
            }

            if (petPayload == null) {
                // try searching by external id field "id"
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", petRef)
                    );
                    CompletableFuture<List<DataPayload>> matchedPetsFuture = entityService.getItemsByCondition(
                        Pet.ENTITY_NAME,
                        Pet.ENTITY_VERSION,
                        condition,
                        true
                    );
                    List<DataPayload> petPayloads = matchedPetsFuture.get();
                    if (petPayloads != null && !petPayloads.isEmpty()) {
                        petPayload = petPayloads.get(0);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to search for pet by external id: {}", ex.getMessage(), ex);
                }
            }

            if (petPayload != null) {
                JsonNode petNode = (JsonNode) petPayload.getData();
                pet = objectMapper.treeToValue(petNode, Pet.class);
            }

            if (pet == null) {
                logger.error("Pet not found for petRef={} on requestId={}", petRef, request.getRequestId());
                request.setStatus("REJECTED");
                request.setDecisionAt(Instant.now().toString());
                request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") +
                    "Pet not found during completion");
                return request;
            }

            // 2) Validate pet availability
            String petStatus = pet.getStatus() != null ? pet.getStatus().trim().toUpperCase() : "";
            if (!"AVAILABLE".equalsIgnoreCase(petStatus) && !"RESERVED".equalsIgnoreCase(petStatus)) {
                logger.error("Pet not available for adoption petTechnicalId={} status={}", pet.getTechnicalId(), pet.getStatus());
                request.setStatus("REJECTED");
                request.setDecisionAt(Instant.now().toString());
                request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") +
                    "Pet not available for adoption");
                return request;
            }

            // 3) Update Pet status -> ADOPTED
            pet.setStatus("ADOPTED");
            try {
                String petTechnicalId = pet.getTechnicalId();
                if (petTechnicalId != null && !petTechnicalId.isBlank()) {
                    CompletableFuture<UUID> updatedPet = entityService.updateItem(UUID.fromString(petTechnicalId), pet);
                    updatedPet.get();
                } else {
                    // If no technicalId available, attempt to find technical id inside payload data
                    JsonNode petNode = (JsonNode) petPayload.getData();
                    JsonNode technicalIdNode = petNode != null ? petNode.get("technicalId") : null;
                    if (technicalIdNode != null && !technicalIdNode.asText().isBlank()) {
                        CompletableFuture<UUID> updatedPet = entityService.updateItem(UUID.fromString(technicalIdNode.asText()), pet);
                        updatedPet.get();
                    } else {
                        logger.warn("Pet technical id missing; cannot update stored pet entity. petRef={}", petRef);
                    }
                }
            } catch (Exception ex) {
                logger.error("Failed to update pet status to ADOPTED: {}", ex.getMessage(), ex);
                // proceed to reject the request to avoid inconsistent state
                request.setStatus("REJECTED");
                request.setDecisionAt(Instant.now().toString());
                request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") +
                    "Failed to update pet status");
                return request;
            }

            // 4) Retrieve Owner (requester) - try by technicalId then by ownerId field
            Owner owner = null;
            DataPayload ownerPayload = null;
            try {
                CompletableFuture<DataPayload> ownerFuture = entityService.getItem(UUID.fromString(requesterRef));
                ownerPayload = ownerFuture.get();
            } catch (Exception ex) {
                ownerPayload = null;
            }

            if (ownerPayload == null) {
                try {
                    SearchConditionRequest ownerCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.ownerId", "EQUALS", requesterRef)
                    );
                    CompletableFuture<List<DataPayload>> ownersFuture = entityService.getItemsByCondition(
                        Owner.ENTITY_NAME,
                        Owner.ENTITY_VERSION,
                        ownerCondition,
                        true
                    );
                    List<DataPayload> ownerPayloads = ownersFuture.get();
                    if (ownerPayloads != null && !ownerPayloads.isEmpty()) {
                        ownerPayload = ownerPayloads.get(0);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to search for owner by ownerId: {}", ex.getMessage(), ex);
                }
            }

            String ownerTechnicalIdForUpdate = null;
            if (ownerPayload != null) {
                JsonNode ownerNode = (JsonNode) ownerPayload.getData();
                owner = objectMapper.treeToValue(ownerNode, Owner.class);
                // try extract technicalId from payload data if present
                if (ownerNode != null && ownerNode.has("technicalId") && !ownerNode.get("technicalId").asText().isBlank()) {
                    ownerTechnicalIdForUpdate = ownerNode.get("technicalId").asText();
                }
            }

            if (owner == null) {
                logger.warn("Owner not found for requesterRef={} on requestId={}. Adoption will complete but owner record not updated.", requesterRef, request.getRequestId());
            } else {
                // 5) Add pet reference to owner's adoptedPets (use pet.id if available, else technicalId)
                String petIdToAdd = pet.getId() != null && !pet.getId().isBlank() ? pet.getId() : pet.getTechnicalId();
                if (petIdToAdd != null && !petIdToAdd.isBlank()) {
                    List<String> adopted = owner.getAdoptedPets();
                    if (adopted == null) {
                        adopted = new ArrayList<>();
                        owner.setAdoptedPets(adopted);
                    }
                    if (!adopted.contains(petIdToAdd)) {
                        adopted.add(petIdToAdd);
                    }
                    // Persist owner update
                    try {
                        if (ownerTechnicalIdForUpdate != null && !ownerTechnicalIdForUpdate.isBlank()) {
                            CompletableFuture<UUID> updatedOwner = entityService.updateItem(UUID.fromString(ownerTechnicalIdForUpdate), owner);
                            updatedOwner.get();
                        } else {
                            // As a last resort, try to update by requesterRef if it looks like a UUID
                            try {
                                UUID maybeUuid = UUID.fromString(requesterRef);
                                CompletableFuture<UUID> updatedOwner = entityService.updateItem(maybeUuid, owner);
                                updatedOwner.get();
                            } catch (Exception ex) {
                                logger.warn("Owner technical id unavailable; cannot update owner record for requesterRef={}", requesterRef);
                            }
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to update owner adoptedPets: {}", ex.getMessage(), ex);
                        // continue; adoption completed on pet side, owner record update failed - note this
                        request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") +
                            "Owner update failed");
                    }
                }
            }

            // 6) Finalize AdoptionRequest
            request.setStatus("COMPLETED");
            request.setDecisionAt(Instant.now().toString());
            // leave reviewerId as-is (ApprovalProcessor likely set it)
            request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") +
                "Adoption completed successfully");

            return request;

        } catch (Exception ex) {
            logger.error("Unexpected error in CompleteAdoptionProcessor: {}", ex.getMessage(), ex);
            request.setStatus("REJECTED");
            request.setDecisionAt(Instant.now().toString());
            request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") +
                "Processor failure: " + ex.getMessage());
            return request;
        }
    }
}