package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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

import java.time.Instant;

@Component
public class ScheduleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ScheduleProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
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

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();

        try {
            // Only transition jobs that are currently scheduled
            String currentState = entity.getState();
            if (currentState == null) {
                logger.warn("Job {} has null state, skipping transition.", entity.getId());
                return entity;
            }

            if (!"SCHEDULED".equalsIgnoreCase(currentState)) {
                logger.info("Job {} is in state '{}' and will not be transitioned by ScheduleProcessor.", entity.getId(), currentState);
                return entity;
            }

            // Transition SCHEDULED -> INGESTING
            entity.setState("INGESTING");
            // Set start time to now (ISO-8601)
            entity.setStartedAt(Instant.now().toString());

            // Initialize counters if missing
            if (entity.getProcessedCount() == null) {
                entity.setProcessedCount(0);
            }
            if (entity.getFailedCount() == null) {
                entity.setFailedCount(0);
            }
            // Clear previous error summary
            entity.setErrorSummary(null);

            logger.info("Job {} transitioned to INGESTING, startedAt={}", entity.getId(), entity.getStartedAt());

            // Note: Do NOT call entityService.updateItem on the job that triggered this workflow.
            // Changing the entity object is sufficient; Cyoda will persist the new state.
            // The transition to INGESTING will trigger downstream IngestionProcessor via the workflow.

        } catch (Exception ex) {
            logger.error("Unexpected error while processing Job {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // Update job to reflect failure to start ingestion where possible
            try {
                if (entity != null) {
                    entity.setState("FAILED");
                    entity.setErrorSummary("ScheduleProcessor error: " + ex.getMessage());
                    entity.setFinishedAt(Instant.now().toString());
                }
            } catch (Exception inner) {
                logger.error("Failed to mark job as FAILED after processing exception: {}", inner.getMessage(), inner);
            }
        }

        return entity;
    }
}