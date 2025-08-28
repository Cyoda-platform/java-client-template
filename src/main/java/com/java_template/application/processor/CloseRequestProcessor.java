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
public class CloseRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CloseRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CloseRequestProcessor(SerializerFactory serializerFactory) {
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

        // Business logic for closing adoption requests:
        // - If the request has status "REJECTED", transition it to "CLOSED".
        // - Append a closure note with timestamp for auditability.
        // - No external entity modifications are performed here; the triggering entity
        //   will be persisted automatically by the workflow.

        if (entity == null) {
            logger.warn("AdoptionRequest entity is null in CloseRequestProcessor");
            return entity;
        }

        String status = entity.getStatus();
        if (status != null && status.equalsIgnoreCase("REJECTED")) {
            logger.info("AdoptionRequest {} is REJECTED - closing request", entity.getRequestId());
            entity.setStatus("CLOSED");

            String timestamp = Instant.now().toString();
            String existingNotes = entity.getNotes();
            String closureNote = "Closed by CloseRequestProcessor at " + timestamp;
            if (existingNotes == null || existingNotes.isBlank()) {
                entity.setNotes(closureNote);
            } else {
                entity.setNotes(existingNotes + " | " + closureNote);
            }
        } else {
            logger.debug("AdoptionRequest {} status is '{}'; no close action applied", entity.getRequestId(), status);
        }

        return entity;
    }
}