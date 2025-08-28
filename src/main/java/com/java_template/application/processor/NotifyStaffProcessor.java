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
            String previousNotes = entity.getNotes();
            String notifyEntry = "Staff notified at " + Instant.now().toString();
            if (previousNotes == null || previousNotes.isBlank()) {
                entity.setNotes(notifyEntry);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(previousNotes);
                sb.append(" | ");
                sb.append(notifyEntry);
                entity.setNotes(sb.toString());
            }

            // Set status to indicate it's under staff review.
            entity.setStatus("UNDER_REVIEW");

            // Do not set processedBy here unless a real processor id is available from context.
            // Leave processedBy unchanged so manual reviewers can populate it.

            logger.info("AdoptionRequest {} moved to UNDER_REVIEW and staff notified.", entity.getId());
        } catch (Exception ex) {
            logger.error("Error while processing AdoptionRequest notification: {}", ex.getMessage(), ex);
            // In case of unexpected error, annotate notes for visibility
            String existing = entity.getNotes();
            String errNote = "NotifyStaffProcessor error: " + ex.getMessage();
            if (existing == null || existing.isBlank()) {
                entity.setNotes(errNote);
            } else {
                entity.setNotes(existing + " | " + errNote);
            }
        }

        return entity;
    }
}