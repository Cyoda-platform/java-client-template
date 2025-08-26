package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JobValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
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
        if (entity == null) return null;

        // Ensure attemptCount is initialized
        if (entity.getAttemptCount() == null) {
            entity.setAttemptCount(0);
        }

        // Defensive check (validate already ran). If invalid, mark FAILED and set completedAt
        if (!entity.isValid()) {
            logger.warn("Job validation failed for entity id (type): {}. Marking as FAILED.", entity.getType());
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
            return entity;
        }

        // If validation passes, prepare job for execution.
        // Set a clear execution status and record start time if not already set.
        String currentStatus = entity.getStatus();
        if (currentStatus == null || currentStatus.isBlank() || "PENDING".equalsIgnoreCase(currentStatus) || "VALIDATING".equalsIgnoreCase(currentStatus)) {
            entity.setStatus("RUNNING");
            if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
                entity.setStartedAt(Instant.now().toString());
            }
            logger.info("Job validated and marked RUNNING. type={}, attemptCount={}", entity.getType(), entity.getAttemptCount());
        } else {
            // If job is already in another state, do not override unintentionally; ensure startedAt exists for RUNNING
            if ("RUNNING".equalsIgnoreCase(currentStatus) && (entity.getStartedAt() == null || entity.getStartedAt().isBlank())) {
                entity.setStartedAt(Instant.now().toString());
            }
            logger.debug("Job validation passed but current status is '{}'. No status change performed.", currentStatus);
        }

        // No updates to other entities are performed here. Cyoda will persist the modified Job automatically.
        return entity;
    }
}