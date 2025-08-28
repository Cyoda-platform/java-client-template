package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class AdoptionRequestApprovedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestApprovedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionRequestApprovedProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        AdoptionRequest entity = context.entity();
        if (entity == null) {
            logger.warn("AdoptionRequest entity is null in execution context");
            return entity;
        }

        // Business logic:
        // 1. When an adoption request is approved (this processor), set the request status to APPROVED.
        // 2. Attempt to set the referenced Pet status to PENDING_ADOPTION only if the Pet exists and is currently AVAILABLE.
        // 3. If Pet does not exist or is not AVAILABLE, mark the request as REJECTED and note reason.

        entity.setStatus("APPROVED");

        String petId = entity.getPetId();
        if (petId == null || petId.isBlank()) {
            logger.error("AdoptionRequest missing petId, rejecting request id={}", entity.getId());
            entity.setStatus("REJECTED");
            entity.setNotes("PetId missing for approval");
            return entity;
        }

        UUID petUuid;
        try {
            petUuid = UUID.fromString(petId);
        } catch (IllegalArgumentException ex) {
            // petId is not a valid UUID - cannot proceed
            logger.error("Invalid petId format for AdoptionRequest id={}: {}", entity.getId(), petId);
            entity.setStatus("REJECTED");
            entity.setNotes("Invalid petId format");
            return entity;
        }

        try {
            // Fetch pet by technical id
            CompletableFuture<DataPayload> petFuture = entityService.getItem(petUuid);
            DataPayload petPayload = petFuture.get();
            if (petPayload == null || petPayload.getData() == null) {
                logger.warn("Referenced pet not found for AdoptionRequest id={}, petId={}", entity.getId(), petId);
                entity.setStatus("REJECTED");
                entity.setNotes("Referenced pet not found");
                return entity;
            }

            // Map payload data to Pet
            Pet pet = objectMapper.treeToValue(petPayload.getData(), Pet.class);
            if (pet == null) {
                logger.warn("Unable to convert pet payload to Pet object for petId={}", petId);
                entity.setStatus("REJECTED");
                entity.setNotes("Failed to retrieve pet data");
                return entity;
            }

            String petStatus = pet.getStatus();
            if (petStatus != null && petStatus.equalsIgnoreCase("AVAILABLE")) {
                // Update pet to pending adoption
                pet.setStatus("PENDING_ADOPTION");
                try {
                    CompletableFuture<UUID> updated = entityService.updateItem(petUuid, pet);
                    UUID updatedId = updated.get();
                    logger.info("Pet {} status updated to PENDING_ADOPTION (update id={}) for adoption request id={}", petId, updatedId, entity.getId());
                    entity.setNotes("Pet status set to PENDING_ADOPTION");
                    // Leave entity.status as APPROVED
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while updating pet for AdoptionRequest id={}", entity.getId(), ie);
                    entity.setStatus("REJECTED");
                    entity.setNotes("Internal error while updating pet");
                } catch (ExecutionException ee) {
                    logger.error("Failed to update pet for AdoptionRequest id={}", entity.getId(), ee);
                    entity.setStatus("REJECTED");
                    entity.setNotes("Failed to update pet status");
                }
            } else {
                logger.warn("Pet not available for adoption (current status='{}') for petId={}, rejecting request id={}", petStatus, petId, entity.getId());
                entity.setStatus("REJECTED");
                entity.setNotes("Pet not available for adoption. Current status: " + (petStatus == null ? "unknown" : petStatus));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching pet for AdoptionRequest id={}", entity.getId(), ie);
            entity.setStatus("REJECTED");
            entity.setNotes("Internal error while fetching pet");
        } catch (ExecutionException ee) {
            logger.error("Error fetching pet for AdoptionRequest id={}", entity.getId(), ee);
            entity.setStatus("REJECTED");
            entity.setNotes("Error fetching pet information");
        } catch (Exception ex) {
            logger.error("Unexpected error processing AdoptionRequest id={}", entity.getId(), ex);
            entity.setStatus("REJECTED");
            entity.setNotes("Unexpected error processing approval");
        }

        return entity;
    }
}