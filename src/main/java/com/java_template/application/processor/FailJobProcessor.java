package com.java_template.application.processor;
import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
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
public class FailJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FailJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FailJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklySendJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklySendJob.class)
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

    private boolean isValidEntity(WeeklySendJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklySendJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklySendJob> context) {
        WeeklySendJob entity = context.entity();

        // Mark job as failed and populate an error message.
        // We use the request id as part of the error message to ensure some context is captured.
        try {
            entity.setStatus("FAILED");
            String requestId = context.request() != null ? context.request().getId() : null;
            String timestamp = Instant.now().toString();
            String computedError = "Job failed at " + timestamp + (requestId != null ? " (requestId=" + requestId + ")" : "");
            entity.setErrorMessage(computedError);

            // Optionally update runAt to indicate when failure was recorded
            entity.setRunAt(timestamp);

            logger.warn("WeeklySendJob marked as FAILED. requestId={}, timestamp={}", requestId, timestamp);
        } catch (Exception ex) {
            logger.error("Unexpected error while processing FailJobProcessor: {}", ex.getMessage(), ex);
            // If something goes wrong here, ensure we still return the entity with a generic failure message
            try {
                entity.setStatus("FAILED");
                entity.setErrorMessage("Failed to mark job as FAILED: " + ex.getMessage());
            } catch (Exception ignore) {
                // best-effort
            }
        }

        return entity;
    }
}