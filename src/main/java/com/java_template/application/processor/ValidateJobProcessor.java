package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidateJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();

        try {
            job.setStatus("VALIDATING");
            job.setStartedAt(job.getStartedAt() == null ? Instant.now().toString() : job.getStartedAt());

            if (job.getRunDate() == null || job.getRunDate().trim().isEmpty()) {
                job.setFailureReason("runDate is required");
                job.setStatus("FAILED");
                logger.warn("Validation failed for job: missing runDate");
                return job;
            }
            // validate runDate format (ISO local date)
            try {
                LocalDate.parse(job.getRunDate());
            } catch (DateTimeParseException e) {
                job.setFailureReason("runDate is not a valid ISO date: " + e.getMessage());
                job.setStatus("FAILED");
                logger.warn("Validation failed for job: invalid runDate {}", job.getRunDate());
                return job;
            }

            if (job.getTimezone() == null || job.getTimezone().trim().isEmpty()) {
                job.setFailureReason("timezone is required");
                job.setStatus("FAILED");
                logger.warn("Validation failed for job: missing timezone");
                return job;
            }

            if (job.getSource() == null || job.getSource().trim().isEmpty()) {
                job.setFailureReason("source is required");
                job.setStatus("FAILED");
                logger.warn("Validation failed for job: missing source");
                return job;
            }

            // idempotency: if jobId provided, check existing jobs with same jobId
            if (job.getJobId() != null && !job.getJobId().trim().isEmpty()) {
                try {
                    // Attempt to find existing job by jobId
                    // For demo: we do not perform lookup; in production use entityService.getItemsByCondition
                } catch (Exception ex) {
                    logger.warn("Error during idempotency check: {}", ex.getMessage());
                }
            }

            // passed basic validation
            job.setFailureReason(null);
            job.setStatus("VALIDATION_PASSED");
            logger.info("Validation passed for ingestion job {}", job.getJobId());
        } catch (Exception ex) {
            logger.error("Unexpected error during ValidateJobProcessor", ex);
            job.setFailureReason("validation error: " + ex.getMessage());
            job.setStatus("FAILED");
        }

        return job;
    }
}
