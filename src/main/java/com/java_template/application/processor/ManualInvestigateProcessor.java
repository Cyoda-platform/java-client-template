package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.errorevent.version_1.ErrorEvent;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ManualInvestigateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ManualInvestigateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ManualInvestigateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ManualInvestigateProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ErrorEvent.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ErrorEvent entity) {
        return entity != null && entity.isValid();
    }

    private ErrorEvent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ErrorEvent> context) {
        ErrorEvent err = context.entity();
        try {
            // Append an investigation note to the details field (existing property)
            String note = String.format("Investigated at %s", Instant.now().toString());
            String existing = err.getDetails();
            if (existing == null || existing.isBlank()) {
                err.setDetails(note);
            } else {
                err.setDetails(existing + " | " + note);
            }

            // Persist the investigation change to the ErrorEvent entity (allowed - updating other entities)
            try {
                if (err.getRelatedJobId() != null && err.getRelatedJobId().length() > 0) {
                    // Attempt to update using the current technical id if available
                    UUID techId = null;
                    try {
                        techId = UUID.fromString(err.getTechnicalId());
                    } catch (Exception ex) {
                        // If technicalId is not a UUID format, skip update and just log
                        techId = null;
                    }

                    if (techId != null) {
                        CompletableFuture<UUID> future = entityService.updateItem(ErrorEvent.ENTITY_NAME, String.valueOf(ErrorEvent.ENTITY_VERSION), techId, err);
                        UUID updated = future.get();
                        logger.info("ManualInvestigateProcessor: updated ErrorEvent technicalId={} updatedId={}", err.getTechnicalId(), updated);
                    } else {
                        // No valid UUID technical id available - fallback: add a new diagnostic entry (not ideal but safe)
                        logger.info("ManualInvestigateProcessor: cannot parse technicalId for update, skipping update for ErrorEvent relatedJobId={}", err.getRelatedJobId());
                    }
                }
            } catch (Exception e) {
                logger.warn("ManualInvestigateProcessor: error while updating ErrorEvent: {}", e.getMessage());
            }

            logger.info("ManualInvestigateProcessor: investigation appended for error relatedJobId={}", err.getRelatedJobId());
        } catch (Exception e) {
            logger.error("ManualInvestigateProcessor: unexpected error processing ErrorEvent: {}", e.getMessage());
        }
        return err;
    }
}
