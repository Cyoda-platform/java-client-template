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

@Component
public class JobRetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobRetryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String RETRY_PREFIX = "retryAttempt=";

    @Autowired
    public JobRetryProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        if (entity == null) {
            return null;
        }

        String currentState = entity.getState();
        if (currentState == null) {
            // nothing to do
            return entity;
        }

        try {
            // Handle failed jobs by attempting retries up to MAX_RETRY_ATTEMPTS.
            if ("FAILED".equalsIgnoreCase(currentState.trim())) {
                String errorSummary = entity.getErrorSummary();
                int currentAttempt = 0;
                String trailingMessage = null;

                if (errorSummary != null && !errorSummary.isBlank()) {
                    // Try to extract existing retryAttempt value and keep other message parts
                    String[] parts = errorSummary.split(";", 2);
                    String first = parts.length > 0 ? parts[0] : "";
                    trailingMessage = parts.length > 1 ? parts[1] : null;

                    if (first != null && first.startsWith(RETRY_PREFIX)) {
                        try {
                            String numStr = first.substring(RETRY_PREFIX.length());
                            currentAttempt = Integer.parseInt(numStr);
                        } catch (NumberFormatException e) {
                            // ignore and treat as zero
                            currentAttempt = 0;
                        }
                    } else {
                        // keep whole errorSummary as trailing message
                        trailingMessage = errorSummary;
                    }
                }

                if (currentAttempt < MAX_RETRY_ATTEMPTS) {
                    int nextAttempt = currentAttempt + 1;
                    StringBuilder newSummary = new StringBuilder();
                    newSummary.append(RETRY_PREFIX).append(nextAttempt);
                    if (trailingMessage != null && !trailingMessage.isBlank()) {
                        newSummary.append(";").append(trailingMessage);
                    }
                    entity.setErrorSummary(newSummary.toString());

                    // Reschedule the job for retry
                    entity.setState("SCHEDULED");
                    // Clear timestamps to indicate a fresh run (will be set by ingestion processor)
                    entity.setStartedAt(null);
                    entity.setFinishedAt(null);

                    logger.info("Job {} marked for retry attempt {}/{}. State set to SCHEDULED.", entity.getId(), nextAttempt, MAX_RETRY_ATTEMPTS);
                } else {
                    // Exceeded retries => move to notification stage so subscribers are informed
                    StringBuilder newSummary = new StringBuilder();
                    if (trailingMessage != null && !trailingMessage.isBlank()) {
                        newSummary.append(trailingMessage).append(";");
                    }
                    newSummary.append("RetryExceeded");
                    entity.setErrorSummary(newSummary.toString());

                    entity.setState("NOTIFIED_SUBSCRIBERS");
                    logger.info("Job {} exceeded retry attempts. State set to NOTIFIED_SUBSCRIBERS.", entity.getId());
                }
            } else {
                // For non-failed states, no retry actions needed. Log for observability.
                logger.debug("Job {} in state '{}': no retry action taken.", entity.getId(), currentState);
            }
        } catch (Exception ex) {
            logger.error("Error while processing retry logic for Job {}: {}", entity.getId(), ex.getMessage(), ex);
            // Record error into errorSummary but do not throw to avoid breaking the workflow
            String prev = entity.getErrorSummary();
            String appended = (prev == null ? "" : prev + ";") + "RetryProcessorError=" + ex.getMessage();
            entity.setErrorSummary(appended);
        }

        return entity;
    }
}