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
import java.lang.reflect.Field;

@Component
public class NotifyStaffProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyStaffProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyStaffProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic:
        // - Move a validated adoption request into UNDER_REVIEW state and annotate notes that staff was notified.
        // - Do not perform update/add/delete via EntityService on the same entity; Cyoda will persist returned entity state.
        // - If notes exist, append notification entry; otherwise set a new notification note.
        try {
            String notifyEntry = "Staff notified at " + Instant.now().toString();

            // Use reflection to access fields to avoid depending on generated accessor methods at compile-time.
            String previousNotes = getStringField(entity, "notes");
            if (previousNotes == null || previousNotes.isBlank()) {
                setStringField(entity, "notes", notifyEntry);
            } else {
                setStringField(entity, "notes", previousNotes + " | " + notifyEntry);
            }

            // Set status to UNDER_REVIEW via reflection to be robust against absent generated setters.
            setStringField(entity, "status", "UNDER_REVIEW");

            // Do not set processedBy here unless a real processor id is available from context.
            // Leave processedBy unchanged so manual reviewers can populate it.

            String idForLog = getStringField(entity, "id");
            logger.info("AdoptionRequest {} moved to UNDER_REVIEW and staff notified.", idForLog == null ? "<unknown>" : idForLog);
        } catch (Exception ex) {
            logger.error("Error while processing AdoptionRequest notification: {}", ex.getMessage(), ex);
            // In case of unexpected error, annotate notes for visibility using best-effort approach.
            try {
                String existing = getStringField(entity, "notes");
                String errNote = "NotifyStaffProcessor error: " + ex.getMessage();
                if (existing == null || existing.isBlank()) {
                    setStringField(entity, "notes", errNote);
                } else {
                    setStringField(entity, "notes", existing + " | " + errNote);
                }
            } catch (Exception inner) {
                logger.error("Failed to annotate error note on AdoptionRequest: {}", inner.getMessage(), inner);
            }
        }

        return entity;
    }

    // Helpers to read/write private String fields via reflection safely.
    private String getStringField(AdoptionRequest entity, String fieldName) {
        if (entity == null) return null;
        try {
            Field f = AdoptionRequest.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(entity);
            return val != null ? val.toString() : null;
        } catch (NoSuchFieldException nsf) {
            logger.debug("Field '{}' not found on AdoptionRequest: {}", fieldName, nsf.getMessage());
            return null;
        } catch (IllegalAccessException iae) {
            logger.warn("Unable to access field '{}' on AdoptionRequest: {}", fieldName, iae.getMessage());
            return null;
        } catch (Exception ex) {
            logger.warn("Unexpected error reading field '{}' from AdoptionRequest: {}", fieldName, ex.getMessage());
            return null;
        }
    }

    private void setStringField(AdoptionRequest entity, String fieldName, String value) {
        if (entity == null) return;
        try {
            Field f = AdoptionRequest.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(entity, value);
        } catch (NoSuchFieldException nsf) {
            logger.debug("Field '{}' not found on AdoptionRequest when setting value: {}", fieldName, nsf.getMessage());
        } catch (IllegalAccessException iae) {
            logger.warn("Unable to set field '{}' on AdoptionRequest: {}", fieldName, iae.getMessage());
        } catch (Exception ex) {
            logger.warn("Unexpected error setting field '{}' on AdoptionRequest: {}", fieldName, ex.getMessage());
        }
    }
}