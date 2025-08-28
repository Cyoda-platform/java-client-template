package com.java_template.application.processor;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

import java.time.Instant;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

@Component
public class RejectAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RejectAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RejectAdoptionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

        // Business logic for rejecting an adoption request:
        // - Mark the request status as REJECTED
        // - Ensure processedBy is set (if missing, mark as manual_rejector)
        // - Append a rejection note with timestamp to existing notes
        // - Do NOT modify the Pet entity here (notifications or other side-effects
        //   should be handled by other processors or external systems)

        String ts = Instant.now().toString();

        String processedBy = null;

        // Try to read processedBy reflectively (getter may not exist)
        try {
            Method getProcessedBy = entity.getClass().getMethod("getProcessedBy");
            Object result = getProcessedBy.invoke(entity);
            if (result != null) {
                processedBy = result.toString();
            }
        } catch (Exception e) {
            // Getter not present or invocation failed; will set a default below
        }

        // If absent, attempt to set processedBy reflectively, otherwise fallback to default value
        if (processedBy == null || processedBy.isBlank()) {
            processedBy = "manual_rejector";
            try {
                Method setProcessedBy = entity.getClass().getMethod("setProcessedBy", String.class);
                setProcessedBy.invoke(entity, processedBy);
            } catch (Exception e) {
                // Unable to set on entity; proceed with fallback value
                logger.debug("Could not set processedBy reflectively on AdoptionRequest: {}", e.getMessage());
            }
        }

        // Update status to REJECTED (assumes setter exists as indicated by compilation output)
        entity.setStatus("REJECTED");

        // Read existing notes reflectively (getter may or may not exist)
        String incomingNote = null;
        try {
            Method getNotes = entity.getClass().getMethod("getNotes");
            Object res = getNotes.invoke(entity);
            incomingNote = res == null ? null : res.toString();
        } catch (Exception e) {
            // Getter not present or invocation failed; treat as no existing notes
        }

        // Prepare new notes content
        String rejectionNote = String.format("Request rejected at %s by %s", ts, processedBy);
        String newNotes;
        if (incomingNote == null || incomingNote.isBlank()) {
            newNotes = rejectionNote;
        } else {
            newNotes = incomingNote + "\n" + rejectionNote;
        }

        // Try to set notes reflectively. If setter is not available, attempt to set the field directly.
        boolean notesSet = false;
        try {
            Method setNotes = entity.getClass().getMethod("setNotes", String.class);
            setNotes.invoke(entity, newNotes);
            notesSet = true;
        } catch (Exception e) {
            // Setter not available; try direct field access
            try {
                Field notesField = entity.getClass().getDeclaredField("notes");
                notesField.setAccessible(true);
                notesField.set(entity, newNotes);
                notesSet = true;
            } catch (Exception ex) {
                logger.debug("Unable to set notes on AdoptionRequest reflectively: {}", ex.getMessage());
            }
        }

        if (!notesSet) {
            logger.warn("Notes could not be updated on AdoptionRequest [{}]; rejection note generated but not persisted on entity.", entity.getId());
        }

        logger.info("AdoptionRequest [{}] for pet [{}] marked as REJECTED by [{}]", entity.getId(), entity.getPetId(), processedBy);

        return entity;
    }
}