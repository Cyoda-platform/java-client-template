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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ApproveAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApproveAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business rule: mark the adoption request as approved when this processor runs
        entity.setStatus("APPROVED");

        // Attempt to mark the referenced pet as pending adoption.
        // We must not modify the triggering entity via EntityService; updating other entities is allowed.
        if (entity.getPetId() != null && !entity.getPetId().isBlank()) {
            UUID petUuid = null;
            try {
                petUuid = UUID.fromString(entity.getPetId());
            } catch (IllegalArgumentException iae) {
                logger.warn("Invalid petId format on AdoptionRequest {}: {}", entity.getId(), entity.getPetId());
                String existing = entity.getNotes();
                String note = "Invalid petId format when approving adoption.";
                entity.setNotes(existing == null ? note : existing + " ; " + note);
                return entity;
            }

            try {
                // Fetch the Pet entity using the EntityService by technical UUID
                CompletableFuture<DataPayload> payloadFuture = entityService.getItem(petUuid);
                DataPayload payload = payloadFuture.get();
                if (payload != null && payload.getData() != null) {
                    Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                    if (pet != null) {
                        String currentStatus = pet.getStatus();
                        // If pet is available (or status is blank), mark it as pending adoption
                        if (currentStatus == null || currentStatus.isBlank() || "AVAILABLE".equalsIgnoreCase(currentStatus)) {
                            pet.setStatus("PENDING_ADOPTION");
                            // Determine the technical id to use for update (prefer pet.getId() if present, otherwise use parsed petUuid)
                            UUID technicalUuid;
                            if (pet.getId() != null && !pet.getId().isBlank()) {
                                try {
                                    technicalUuid = UUID.fromString(pet.getId());
                                } catch (IllegalArgumentException iae) {
                                    // fallback to the original petUuid
                                    technicalUuid = petUuid;
                                }
                            } else {
                                technicalUuid = petUuid;
                            }
                            // Persist the pet update
                            entityService.updateItem(technicalUuid, pet).get();
                            logger.info("Marked pet {} as PENDING_ADOPTION due to approved adoption request {}", technicalUuid, entity.getId());
                            String existingNotes = entity.getNotes();
                            entity.setNotes(existingNotes == null ? "Pet status set to PENDING_ADOPTION" : existingNotes + " ; Pet status set to PENDING_ADOPTION");
                        } else {
                            logger.info("Pet {} not in AVAILABLE state (current: {}), skipping status change", pet.getId(), currentStatus);
                            String existingNotes = entity.getNotes();
                            entity.setNotes(existingNotes == null ? "Pet not available for adoption" : existingNotes + " ; Pet not available for adoption");
                        }
                    } else {
                        String note = "Referenced pet could not be deserialized when approving adoption.";
                        String existing = entity.getNotes();
                        entity.setNotes(existing == null ? note : existing + " ; " + note);
                        logger.warn("Deserialized pet is null for petId {}", entity.getPetId());
                    }
                } else {
                    String note = "Referenced pet not found when approving adoption.";
                    String existing = entity.getNotes();
                    entity.setNotes(existing == null ? note : existing + " ; " + note);
                    logger.warn("Pet payload null for petId {}", entity.getPetId());
                }
            } catch (Exception ex) {
                logger.warn("Failed to update pet status for petId {}: {}", entity.getPetId(), ex.getMessage());
                String append = "Failed to mark pet as pending adoption: " + ex.getMessage();
                String notes = entity.getNotes();
                entity.setNotes(notes == null ? append : notes + " ; " + append);
            }
        } else {
            String append = "Approval executed but petId was missing on request.";
            String notes = entity.getNotes();
            entity.setNotes(notes == null ? append : notes + " ; " + append);
            logger.warn("AdoptionRequest {} has no petId", entity.getId());
        }

        // processedBy and other auditing can be set by external systems or subsequent processors.
        return entity;
    }
}