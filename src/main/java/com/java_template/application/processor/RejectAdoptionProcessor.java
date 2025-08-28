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

        // Set processedBy if absent
        if (entity.getProcessedBy() == null || entity.getProcessedBy().isBlank()) {
            entity.setProcessedBy("manual_rejector");
        }

        // Update status to REJECTED
        entity.setStatus("REJECTED");

        // Append rejection note
        String incomingNote = entity.getNotes();
        String rejectionNote = String.format("Request rejected at %s by %s", ts, entity.getProcessedBy());
        if (incomingNote == null || incomingNote.isBlank()) {
            entity.setNotes(rejectionNote);
        } else {
            entity.setNotes(incomingNote + "\n" + rejectionNote);
        }

        logger.info("AdoptionRequest [{}] for pet [{}] marked as REJECTED by [{}]", entity.getId(), entity.getPetId(), entity.getProcessedBy());

        return entity;
    }
}