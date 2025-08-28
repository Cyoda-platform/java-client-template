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
public class CancelRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CancelRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CancelRequestProcessor(SerializerFactory serializerFactory) {
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

        // Business rule: A requester may cancel the adoption request only if it has not been decided yet.
        // Allowed cancel states: "submitted", "under_review"
        // If current status is one of allowed states, mark as "cancelled" and set decisionAt timestamp.
        // Otherwise, do not change status (but log the attempted cancellation).

        if (entity == null) {
            logger.warn("AdoptionRequest entity is null in CancelRequestProcessor");
            return entity;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus == null) {
            logger.warn("AdoptionRequest {} has null status; cannot process cancellation", entity.getRequestId());
            return entity;
        }

        String statusLower = currentStatus.toLowerCase();

        if ("submitted".equals(statusLower) || "under_review".equals(statusLower)) {
            entity.setStatus("cancelled");
            String now = Instant.now().toString();
            entity.setDecisionAt(now);

            String existingNotes = entity.getNotes();
            String cancelNote = "Cancelled by requester at " + now;
            if (existingNotes == null || existingNotes.isBlank()) {
                entity.setNotes(cancelNote);
            } else {
                entity.setNotes(existingNotes + "\n" + cancelNote);
            }

            logger.info("AdoptionRequest {} cancelled by requester", entity.getRequestId());
        } else {
            logger.warn("AdoptionRequest {} cannot be cancelled from status '{}'", entity.getRequestId(), currentStatus);
            String now = Instant.now().toString();
            String existingNotes = entity.getNotes();
            String infoNote = "Cancel attempted at " + now + " but request is in status '" + currentStatus + "' and cannot be cancelled.";
            if (existingNotes == null || existingNotes.isBlank()) {
                entity.setNotes(infoNote);
            } else {
                entity.setNotes(existingNotes + "\n" + infoNote);
            }
        }

        return entity;
    }
}