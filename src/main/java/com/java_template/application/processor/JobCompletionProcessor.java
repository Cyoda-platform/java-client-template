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
import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class JobCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public JobCompletionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        Job job = context.entity();
        try {
            if (job == null) return null;

            String currentState = job.getState();
            if (currentState == null) {
                logger.warn("Job state is null for job id={}. Leaving unchanged.", job.getId());
                return job;
            }

            // Only handle completion when job is in INGESTING state
            if ("INGESTING".equalsIgnoreCase(currentState)) {
                // Ensure processedCount is non-null
                if (job.getProcessedCount() == null) {
                    job.setProcessedCount(0);
                }
                // Ensure failedCount is non-null
                if (job.getFailedCount() == null) {
                    job.setFailedCount(0);
                }

                // Set finished timestamp
                job.setFinishedAt(Instant.now().toString());

                // Determine final state based on failedCount
                if (job.getFailedCount() != null && job.getFailedCount() > 0) {
                    job.setState("FAILED");
                    String summary = job.getErrorSummary();
                    if (summary == null || summary.isBlank()) {
                        summary = "Ingestion completed with failures: " + job.getFailedCount();
                    } else {
                        summary = summary + " | Failures: " + job.getFailedCount();
                    }
                    job.setErrorSummary(summary);
                    logger.info("Job {} completed with failures. processedCount={}, failedCount={}", job.getId(), job.getProcessedCount(), job.getFailedCount());
                } else {
                    job.setState("SUCCEEDED");
                    // Clear any previous error summary if none failed
                    if (job.getErrorSummary() != null && job.getErrorSummary().isBlank()) {
                        job.setErrorSummary(null);
                    }
                    logger.info("Job {} succeeded. processedCount={}", job.getId(), job.getProcessedCount());
                }
            } else {
                logger.debug("Job {} not in INGESTING state (current={}). No completion action taken.", job.getId(), currentState);
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while completing Job {}: {}", job != null ? job.getId() : "unknown", ex.getMessage(), ex);
            if (job != null) {
                job.setState("FAILED");
                job.setFinishedAt(Instant.now().toString());
                String prev = job.getErrorSummary();
                job.setErrorSummary((prev == null ? "" : prev + " | ") + "CompletionProcessorError: " + ex.getMessage());
            }
        }
        return job;
    }
}