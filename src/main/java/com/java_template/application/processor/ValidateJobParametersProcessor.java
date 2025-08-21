package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.batchjob.version_1.BatchJob;
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
import java.time.format.DateTimeParseException;

@Component
public class ValidateJobParametersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobParametersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobParametersProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BatchJob validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid batch job payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob job) {
        return job != null && job.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob job = context.entity();
        try {
            // Mark as validating
            job.setStatus("VALIDATING");
            // Ensure createdAt is set
            if (job.getCreatedAt() == null || job.getCreatedAt().isBlank()) {
                job.setCreatedAt(Instant.now().toString());
            }

            // Validate scheduledFor format if present
            if (job.getScheduledFor() != null && !job.getScheduledFor().isBlank()) {
                try {
                    Instant.parse(job.getScheduledFor());
                } catch (DateTimeParseException ex) {
                    job.setStatus("FAILED");
                    job.setErrorMessage("scheduledFor must be an ISO-8601 datetime");
                    logger.warn("BatchJob {} failed validation - invalid scheduledFor: {}", job.getJobName(), job.getScheduledFor());
                    return job;
                }
            }

            // timezone should be present
            if (job.getTimezone() == null || job.getTimezone().isBlank()) {
                job.setStatus("FAILED");
                job.setErrorMessage("timezone is required");
                return job;
            }

            // admin emails must be present
            if (job.getAdminEmails() == null || job.getAdminEmails().isEmpty()) {
                job.setStatus("FAILED");
                job.setErrorMessage("adminEmails is required");
                return job;
            }

            // If passed validation keep status as VALIDATING; next criteria will route it
            logger.info("BatchJob {} validation passed", job.getJobName());
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error during ValidateJobParametersProcessor", ex);
            job.setStatus("FAILED");
            job.setErrorMessage("Unexpected error during validation: " + ex.getMessage());
            return job;
        }
    }
}
