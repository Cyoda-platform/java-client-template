package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PetStatusUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetStatusUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetStatusUpdateProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest request = context.entity();

        // Business logic:
        // - Find the Pet referenced by adoptionRequest.petId
        // - If Pet not found -> log and return
        // - If request status indicates a pending review/submission -> set Pet.status = "pending"
        // - If request status indicates approval -> set Pet.status = "adopted"
        // - Update Pet via EntityService (do not modify the AdoptionRequest via EntityService)

        if (request == null) {
            logger.warn("AdoptionRequest is null in execution context");
            return request;
        }

        String petBusinessId = request.getPetId();
        if (petBusinessId == null || petBusinessId.isBlank()) {
            logger.warn("AdoptionRequest {} has no petId", request.getId());
            return request;
        }

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", petBusinessId)
            );

            CompletableFuture<List<DataPayload>> future = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                Pet.ENTITY_VERSION,
                condition,
                true
            );
            List<DataPayload> dataPayloads = future.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.warn("No Pet found with business id {} for AdoptionRequest {}", petBusinessId, request.getId());
                return request;
            }

            // Use the first matched pet
            DataPayload petPayload = dataPayloads.get(0);
            // Extract technical id for update
            String technicalId = petPayload.getTechnicalId() != null ? petPayload.getTechnicalId() : petPayload.getId();

            Pet pet = objectMapper.treeToValue(petPayload.getData(), Pet.class);
            if (pet == null) {
                logger.warn("Failed to deserialize Pet payload for business id {} (AdoptionRequest {})", petBusinessId, request.getId());
                return request;
            }

            String reqStatus = request.getStatus();
            if (reqStatus == null) reqStatus = "";

            // Default behavior: when a request is submitted/under_review -> put pet on "pending"
            if (reqStatus.equalsIgnoreCase("submitted") || reqStatus.equalsIgnoreCase("under_review") || reqStatus.equalsIgnoreCase("pending")) {
                pet.setStatus("pending");
            }

            // When request approved -> set pet to adopted
            if (reqStatus.equalsIgnoreCase("approved")) {
                pet.setStatus("adopted");
            }

            // When request rejected or cancelled, we do not force change here (could be handled elsewhere)
            pet.setUpdatedAt(Instant.now().toString());

            // Persist updated pet
            if (technicalId != null && !technicalId.isBlank()) {
                UUID petTechnicalUuid = UUID.fromString(technicalId);
                CompletableFuture<UUID> updated = entityService.updateItem(petTechnicalUuid, pet);
                UUID updatedId = updated.get();
                logger.info("Updated Pet (technicalId={}) status to '{}' for AdoptionRequest {}. update returned id={}",
                    technicalId, pet.getStatus(), request.getId(), updatedId);
            } else {
                logger.warn("Pet technical id missing for business id {}. Skipping update.", petBusinessId);
            }

        } catch (Exception ex) {
            logger.error("Error while updating Pet for AdoptionRequest {}: {}", request.getId(), ex.getMessage(), ex);
        }

        return request;
    }
}