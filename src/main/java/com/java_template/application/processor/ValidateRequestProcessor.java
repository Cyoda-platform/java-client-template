package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ValidateRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidateRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Initialize notes if null (use reflection to read notes getter/field if present)
        String existingNotes = getExistingNotes(entity);
        StringBuilder notesBuilder = new StringBuilder(existingNotes);

        // Basic business validations
        // 1) Ensure motivation is provided
        if (entity.getMotivation() == null || entity.getMotivation().isBlank()) {
            entity.setStatus("REJECTED");
            if (notesBuilder.length() > 0) notesBuilder.append(" | ");
            notesBuilder.append("Missing motivation.");
            entity.setNotes(notesBuilder.toString());
            logger.info("AdoptionRequest {} rejected: missing motivation", entity.getId());
            return entity;
        }

        // 2) Ensure contactEmail or contactPhone is present (isValid already checks contactEmail, but be permissive)
        boolean hasEmail = entity.getContactEmail() != null && !entity.getContactEmail().isBlank();
        boolean hasPhone = entity.getContactPhone() != null && !entity.getContactPhone().isBlank();
        if (!hasEmail && !hasPhone) {
            entity.setStatus("REJECTED");
            if (notesBuilder.length() > 0) notesBuilder.append(" | ");
            notesBuilder.append("Missing contact information (email or phone).");
            entity.setNotes(notesBuilder.toString());
            logger.info("AdoptionRequest {} rejected: missing contact information", entity.getId());
            return entity;
        }

        // 3) Validate referenced Pet exists and is AVAILABLE
        if (entity.getPetId() == null || entity.getPetId().isBlank()) {
            entity.setStatus("REJECTED");
            if (notesBuilder.length() > 0) notesBuilder.append(" | ");
            notesBuilder.append("Missing petId reference.");
            entity.setNotes(notesBuilder.toString());
            logger.info("AdoptionRequest {} rejected: missing petId", entity.getId());
            return entity;
        }

        try {
            // Call EntityService#getItem with the petId converted to UUID to match interface signature
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(entity.getPetId()));
            DataPayload payload = itemFuture.get();

            if (payload == null || payload.getData() == null) {
                entity.setStatus("REJECTED");
                if (notesBuilder.length() > 0) notesBuilder.append(" | ");
                notesBuilder.append("Referenced pet not found.");
                entity.setNotes(notesBuilder.toString());
                logger.info("AdoptionRequest {} rejected: pet not found ({})", entity.getId(), entity.getPetId());
                return entity;
            }

            Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
            if (pet == null) {
                entity.setStatus("REJECTED");
                if (notesBuilder.length() > 0) notesBuilder.append(" | ");
                notesBuilder.append("Referenced pet data invalid.");
                entity.setNotes(notesBuilder.toString());
                logger.info("AdoptionRequest {} rejected: pet data invalid ({})", entity.getId(), entity.getPetId());
                return entity;
            }

            String petStatus = pet.getStatus();
            if (petStatus == null || !petStatus.equalsIgnoreCase("AVAILABLE")) {
                entity.setStatus("REJECTED");
                if (notesBuilder.length() > 0) notesBuilder.append(" | ");
                notesBuilder.append("Pet not available. Current pet status: ").append(petStatus == null ? "unknown" : petStatus);
                entity.setNotes(notesBuilder.toString());
                logger.info("AdoptionRequest {} rejected: pet not available (status={})", entity.getId(), petStatus);
                return entity;
            }

            // If all checks pass, move request to UNDER_REVIEW
            entity.setStatus("UNDER_REVIEW");
            if (notesBuilder.length() > 0) notesBuilder.append(" | ");
            notesBuilder.append("Request validated and pet is available.");
            entity.setNotes(notesBuilder.toString());
            logger.info("AdoptionRequest {} validated and moved to UNDER_REVIEW", entity.getId());
            return entity;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            entity.setStatus("REJECTED");
            if (notesBuilder.length() > 0) notesBuilder.append(" | ");
            notesBuilder.append("Interrupted while validating pet reference.");
            entity.setNotes(notesBuilder.toString());
            logger.error("Interrupted while fetching pet for AdoptionRequest {}: {}", entity.getId(), ie.getMessage(), ie);
            return entity;
        } catch (ExecutionException ee) {
            entity.setStatus("REJECTED");
            if (notesBuilder.length() > 0) notesBuilder.append(" | ");
            notesBuilder.append("Failed to fetch referenced pet: ").append(ee.getCause() != null ? ee.getCause().getMessage() : ee.getMessage());
            entity.setNotes(notesBuilder.toString());
            logger.error("Failed to fetch pet for AdoptionRequest {}: {}", entity.getId(), ee.getMessage(), ee);
            return entity;
        } catch (Exception ex) {
            entity.setStatus("REJECTED");
            if (notesBuilder.length() > 0) notesBuilder.append(" | ");
            notesBuilder.append("Unexpected error during validation: ").append(ex.getMessage());
            entity.setNotes(notesBuilder.toString());
            logger.error("Unexpected error validating AdoptionRequest {}: {}", entity.getId(), ex.getMessage(), ex);
            return entity;
        }
    }

    /**
     * Attempts to read existing notes from the AdoptionRequest using a getter or direct field access.
     * Returns empty string if notes cannot be read.
     */
    private String getExistingNotes(AdoptionRequest entity) {
        if (entity == null) return "";
        try {
            Method m = entity.getClass().getMethod("getNotes");
            Object val = m.invoke(entity);
            return val == null ? "" : val.toString().trim();
        } catch (NoSuchMethodException ignored) {
            // try field access
            try {
                Field f = entity.getClass().getDeclaredField("notes");
                f.setAccessible(true);
                Object val = f.get(entity);
                return val == null ? "" : val.toString().trim();
            } catch (Exception ignored2) {
                return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}