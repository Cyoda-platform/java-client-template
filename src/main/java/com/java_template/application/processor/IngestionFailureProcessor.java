package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class IngestionFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestionFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IngestionFailureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Mark job as FAILED and update timestamps / counters
        try {
            logger.info("Marking job '{}' as FAILED", entity.getId());

            // Update state
            entity.setState("FAILED");

            // Set finishedAt to current timestamp in ISO-8601
            String finishedAt = Instant.now().toString();
            entity.setFinishedAt(finishedAt);

            // Increment failedCount (safely handle null)
            Integer currentFailed = entity.getFailedCount();
            if (currentFailed == null) currentFailed = 0;
            entity.setFailedCount(currentFailed + 1);

            // Ensure processedCount is non-null (leave as-is if present)
            if (entity.getProcessedCount() == null) {
                entity.setProcessedCount(0);
            }

            // Set or augment error summary
            String currentSummary = entity.getErrorSummary();
            if (currentSummary == null || currentSummary.isBlank()) {
                entity.setErrorSummary("Ingestion failed at " + finishedAt);
            } else {
                entity.setErrorSummary(currentSummary + " | Failed at " + finishedAt);
            }

            logger.info("Job '{}' updated: state={}, processedCount={}, failedCount={}, finishedAt={}",
                entity.getId(), entity.getState(), entity.getProcessedCount(), entity.getFailedCount(), entity.getFinishedAt());

        } catch (Exception ex) {
            logger.error("Unexpected error while processing ingestion failure for job {}: {}", entity.getId(), ex.getMessage(), ex);
            // Don't throw; let serializer complete and persist current entity state if possible
            String now = Instant.now().toString();
            if (entity.getFinishedAt() == null) entity.setFinishedAt(now);
            if (entity.getState() == null || entity.getState().isBlank()) entity.setState("FAILED");
            if (entity.getErrorSummary() == null) entity.setErrorSummary("Processing error while marking failure: " + ex.getMessage());
        }

        return entity;
    }
}