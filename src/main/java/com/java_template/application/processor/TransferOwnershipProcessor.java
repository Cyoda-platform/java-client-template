package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
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

import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Component
public class TransferOwnershipProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransferOwnershipProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TransferOwnershipProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Basic null check
        if (request == null) {
            logger.warn("AdoptionRequest is null in execution context");
            return request;
        }

        // Only proceed when adoption request is approved.
        if (request.getStatus() == null || !"APPROVED".equalsIgnoreCase(request.getStatus())) {
            logger.info("AdoptionRequest {} is not APPROVED. Current status: {}", request.getId(), request.getStatus());
            return request;
        }

        String petTechnicalId = request.getPetId();
        if (petTechnicalId == null || petTechnicalId.isBlank()) {
            logger.error("AdoptionRequest {} missing petId", request.getId());
            request.setStatus("FAILED");
            request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") + "Missing petId");
            return request;
        }

        try {
            // EntityService.getItem expects a UUID only (technicalId). Use the single-arg form.
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(petTechnicalId));
            DataPayload payload = itemFuture.get();
            if (payload == null || payload.getData() == null) {
                logger.error("Pet not found for id {}", petTechnicalId);
                request.setStatus("FAILED");
                request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") + "Pet not found: " + petTechnicalId);
                return request;
            }

            Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
            if (pet == null) {
                logger.error("Failed to deserialize Pet for id {}", petTechnicalId);
                request.setStatus("FAILED");
                request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") + "Failed to deserialize Pet");
                return request;
            }

            // Ensure the pet is available for adoption
            if (pet.getStatus() == null || !"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
                logger.warn("Pet {} not available for adoption. Current status: {}", pet.getId(), pet.getStatus());
                request.setStatus("FAILED");
                request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") + "Pet not available: " + pet.getStatus());
                return request;
            }

            // Update pet status to ADOPTED
            pet.setStatus("ADOPTED");

            // Pet entity has no owner field, so attach adopter info to tags to preserve adopter reference
            List<String> tags = pet.getTags();
            if (tags == null) {
                tags = new ArrayList<>();
            }
            String adopterName = request.getRequesterName() != null ? request.getRequesterName() : "unknown";
            String adopterTag = "adopted-by:" + adopterName;
            tags.add(adopterTag);
            pet.setTags(tags);

            // Persist pet update using EntityService (we are allowed to update other entities)
            if (pet.getId() == null || pet.getId().isBlank()) {
                logger.error("Pet entity has no technical id, cannot update");
                request.setStatus("FAILED");
                request.setNotes((request.getNotes() == null ? "" : request.getNotes() + " | ") + "Pet missing technical id");
                return request;
            }

            CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(pet.getId()), pet);
            updated.get(); // wait for completion

            // Mark adoption request as completed
            request.setStatus("COMPLETED");
            request.setProcessedBy(request.getProcessedBy() == null ? "system" : request.getProcessedBy());
            String existingNotes = request.getNotes() == null ? "" : request.getNotes() + " | ";
            request.setNotes(existingNotes + "Adoption completed for petId: " + pet.getId());

            logger.info("Successfully transferred ownership for pet {} via request {}", pet.getId(), request.getId());
            return request;
        } catch (Exception e) {
            logger.error("Error while transferring ownership for request {}: {}", request.getId(), e.getMessage(), e);
            request.setStatus("FAILED");
            String existingNotes = request.getNotes() == null ? "" : request.getNotes() + " | ";
            request.setNotes(existingNotes + "Error: " + e.getMessage());
            return request;
        }
    }
}