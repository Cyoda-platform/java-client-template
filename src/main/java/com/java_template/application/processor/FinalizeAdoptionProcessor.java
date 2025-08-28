package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class FinalizeAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FinalizeAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
                    logger.error("Failed to extract AdoptionRequest entity: {}", error.getMessage(), error);
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
        AdoptionRequest adoptionRequest = context.entity();
        try {
            // Only finalize if payment is confirmed (PAID)
            if (adoptionRequest.getPaymentStatus() == null ||
                !adoptionRequest.getPaymentStatus().equalsIgnoreCase("PAID")) {
                logger.info("Payment not confirmed for requestId={}, paymentStatus={}. Skipping finalization.",
                        adoptionRequest.getRequestId(), adoptionRequest.getPaymentStatus());
                return adoptionRequest;
            }

            // 1. Locate the Pet by business petId
            SearchConditionRequest petCondition = SearchConditionRequest.group("AND",
                Condition.of("$.petId", "EQUALS", adoptionRequest.getPetId())
            );

            CompletableFuture<List<DataPayload>> petFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME, Pet.ENTITY_VERSION, petCondition, true
            );
            List<DataPayload> petPayloads = petFuture.get();

            if (petPayloads == null || petPayloads.isEmpty()) {
                logger.error("Pet not found for petId={} while finalizing adoption requestId={}", adoptionRequest.getPetId(), adoptionRequest.getRequestId());
                String existingNotes = adoptionRequest.getNotes() == null ? "" : adoptionRequest.getNotes() + " | ";
                adoptionRequest.setNotes(existingNotes + "Pet not found during finalization.");
                // Set status closed to avoid retry loops
                adoptionRequest.setStatus("CLOSED");
                return adoptionRequest;
            }

            // Use first matching pet
            DataPayload petPayload = petPayloads.get(0);
            JsonNode petMeta = petPayload.getMeta();
            String petTechnicalId = petMeta != null && petMeta.get("entityId") != null ? petMeta.get("entityId").asText() : null;
            Pet pet = objectMapper.treeToValue(petPayload.getData(), Pet.class);

            if (pet == null) {
                logger.error("Failed to deserialize Pet for petId={} requestId={}", adoptionRequest.getPetId(), adoptionRequest.getRequestId());
                adoptionRequest.setStatus("CLOSED");
                return adoptionRequest;
            }

            // Check pet availability/reservation before adoption
            String petStatus = pet.getStatus();
            if (petStatus != null && petStatus.equalsIgnoreCase("Adopted")) {
                logger.info("Pet already adopted petId={} for requestId={}", pet.getPetId(), adoptionRequest.getRequestId());
                adoptionRequest.setStatus("COMPLETED");
                return adoptionRequest;
            }

            // Proceed to mark pet as Adopted
            pet.setStatus("Adopted");
            if (petTechnicalId != null) {
                try {
                    CompletableFuture<UUID> updatePetFuture = entityService.updateItem(UUID.fromString(petTechnicalId), pet);
                    updatePetFuture.get();
                    logger.info("Updated Pet status to Adopted for petId={}, technicalId={}", pet.getPetId(), petTechnicalId);
                } catch (Exception e) {
                    logger.error("Failed to update Pet entity for petId={} technicalId={}: {}", pet.getPetId(), petTechnicalId, e.getMessage(), e);
                    throw new RuntimeException("Failed to update Pet during finalization: " + e.getMessage(), e);
                }
            } else {
                logger.error("Missing technicalId for Pet petId={} during finalization", pet.getPetId());
                throw new RuntimeException("Missing Pet technical id");
            }

            // 2. Locate the User by business userId and append adoptedPetIds
            SearchConditionRequest userCondition = SearchConditionRequest.group("AND",
                Condition.of("$.userId", "EQUALS", adoptionRequest.getUserId())
            );

            CompletableFuture<List<DataPayload>> userFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME, User.ENTITY_VERSION, userCondition, true
            );
            List<DataPayload> userPayloads = userFuture.get();

            if (userPayloads == null || userPayloads.isEmpty()) {
                logger.warn("User not found for userId={} while finalizing adoption requestId={}", adoptionRequest.getUserId(), adoptionRequest.getRequestId());
                // Still mark the request completed even if user record is missing
                adoptionRequest.setStatus("COMPLETED");
                return adoptionRequest;
            }

            DataPayload userPayload = userPayloads.get(0);
            JsonNode userMeta = userPayload.getMeta();
            String userTechnicalId = userMeta != null && userMeta.get("entityId") != null ? userMeta.get("entityId").asText() : null;
            User user = objectMapper.treeToValue(userPayload.getData(), User.class);

            if (user == null) {
                logger.warn("Failed to deserialize User for userId={} requestId={}", adoptionRequest.getUserId(), adoptionRequest.getRequestId());
                adoptionRequest.setStatus("COMPLETED");
                return adoptionRequest;
            }

            List<String> adoptedPetIds = user.getAdoptedPetIds();
            if (adoptedPetIds == null) {
                adoptedPetIds = new ArrayList<>();
            }
            String adoptedPetBusinessId = pet.getPetId();
            if (adoptedPetBusinessId != null && !adoptedPetIds.contains(adoptedPetBusinessId)) {
                adoptedPetIds.add(adoptedPetBusinessId);
                user.setAdoptedPetIds(adoptedPetIds);
                if (userTechnicalId != null) {
                    try {
                        CompletableFuture<UUID> updateUserFuture = entityService.updateItem(UUID.fromString(userTechnicalId), user);
                        updateUserFuture.get();
                        logger.info("Updated User adoptedPetIds for userId={}, technicalId={}", user.getUserId(), userTechnicalId);
                    } catch (Exception e) {
                        logger.error("Failed to update User entity for userId={} technicalId={}: {}", user.getUserId(), userTechnicalId, e.getMessage(), e);
                        throw new RuntimeException("Failed to update User during finalization: " + e.getMessage(), e);
                    }
                } else {
                    logger.error("Missing technicalId for User userId={} during finalization", user.getUserId());
                    throw new RuntimeException("Missing User technical id");
                }
            } else {
                logger.info("User already contains adoptedPetId or petId is null for userId={}", user.getUserId());
            }

            // 3. Mark the adoption request as COMPLETED
            adoptionRequest.setStatus("COMPLETED");
            logger.info("AdoptionRequest {} finalized and marked COMPLETED", adoptionRequest.getRequestId());
            return adoptionRequest;

        } catch (Exception ex) {
            logger.error("Error finalizing adoption request {}: {}", adoptionRequest != null ? adoptionRequest.getRequestId() : "unknown", ex.getMessage(), ex);
            throw new RuntimeException("FinalizeAdoptionProcessor failed: " + ex.getMessage(), ex);
        }
    }
}