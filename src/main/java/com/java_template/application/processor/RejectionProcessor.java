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
public class RejectionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RejectionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RejectionProcessor(SerializerFactory serializerFactory) {
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
        if (entity == null) {
            return entity;
        }

        String currentStatus = entity.getStatus() == null ? "" : entity.getStatus().trim().toLowerCase();

        // If already in a terminal or final state, do not modify
        if ("rejected".equalsIgnoreCase(currentStatus) ||
            "approved".equalsIgnoreCase(currentStatus) ||
            "completed".equalsIgnoreCase(currentStatus) ||
            "cancelled".equalsIgnoreCase(currentStatus)) {
            logger.info("AdoptionRequest {} is in terminal state '{}'; skipping rejection processing.", entity.getRequestId(), entity.getStatus());
            return entity;
        }

        // Set rejection state and decision timestamp. Preserve reviewerId if already set.
        entity.setStatus("rejected");
        if (entity.getDecisionAt() == null || entity.getDecisionAt().isBlank()) {
            entity.setDecisionAt(Instant.now().toString());
        }

        // Ensure there is a human-readable note indicating rejection if none provided
        if (entity.getNotes() == null || entity.getNotes().isBlank()) {
            entity.setNotes("Request rejected.");
        } else {
            // append brief rejection marker if notes already exist
            entity.setNotes(entity.getNotes() + " (marked rejected)");
        }

        logger.info("AdoptionRequest {} marked as rejected at {}", entity.getRequestId(), entity.getDecisionAt());

        return entity;
    }
}