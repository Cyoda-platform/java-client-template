package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid ingestion job: missing or invalid required fields")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        if (entity == null) return false;
        // sourceEndpoint must be present
        String source = entity.getSourceEndpoint();
        if (source == null || source.isBlank()) {
            logger.warn("IngestionJob validation failed: sourceEndpoint is missing");
            return false;
        }
        // schedule must be present and look like a cron or weekly descriptor (basic check)
        String schedule = entity.getSchedule();
        if (schedule == null || schedule.isBlank()) {
            logger.warn("IngestionJob validation failed: schedule is missing");
            return false;
        }
        if (!looksLikeSchedule(schedule)) {
            logger.warn("IngestionJob validation failed: schedule looks invalid: {}", schedule);
            return false;
        }
        // initiatedBy should be present
        String initiatedBy = entity.getInitiatedBy();
        if (initiatedBy == null || initiatedBy.isBlank()) {
            logger.warn("IngestionJob validation failed: initiatedBy is missing");
            return false;
        }
        // status should be present and typically PENDING when starting validation
        String status = entity.getStatus();
        if (status == null || status.isBlank()) {
            logger.warn("IngestionJob validation failed: status is missing");
            return false;
        }
        return true;
    }

    private boolean looksLikeSchedule(String schedule) {
        // basic heuristic: cron expressions typically have 5 or 6 space-separated parts,
        // weekly descriptors often contain letters and numbers; accept if >=5 segments or contains non-space chars.
        String[] parts = schedule.trim().split("\\s+");
        if (parts.length >= 5) return true;
        // also allow simple weekly words like "weekly" or cron-like with '?' or '/'
        if (schedule.contains("/") || schedule.contains("?") || schedule.equalsIgnoreCase("weekly") || schedule.equalsIgnoreCase("daily")) {
            return true;
        }
        return false;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();
        logger.info("ValidateJobProcessor executing business logic for jobId={}", job.getJobId());

        // Set status to RUNNING and initialize runtime fields.
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now().toString());

        // Ensure processedCount initialized to zero if not present
        if (job.getProcessedCount() == null) {
            job.setProcessedCount(0);
        }

        // Clear previous error summary when starting a fresh run
        job.setErrorSummary(null);

        logger.info("IngestionJob {} set to RUNNING (startedAt={})", job.getJobId(), job.getStartedAt());
        return job;
    }
}