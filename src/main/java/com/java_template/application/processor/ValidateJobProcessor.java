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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        if (entity == null) {
            logger.warn("IngestionJob is null");
            return false;
        }
        // Required fields: sourceEndpoint, schedule, initiatedBy, status should be PENDING for validation to start
        if (entity.getSourceEndpoint() == null || entity.getSourceEndpoint().isBlank()) {
            logger.warn("IngestionJob.sourceEndpoint is missing or blank");
            return false;
        }
        if (entity.getSchedule() == null || entity.getSchedule().isBlank()) {
            logger.warn("IngestionJob.schedule is missing or blank");
            return false;
        }
        if (entity.getInitiatedBy() == null || entity.getInitiatedBy().isBlank()) {
            logger.warn("IngestionJob.initiatedBy is missing or blank");
            return false;
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            logger.warn("IngestionJob.status is missing or blank");
            return false;
        }
        // Only validate jobs that are in PENDING state
        if (!"PENDING".equalsIgnoreCase(entity.getStatus())) {
            logger.warn("IngestionJob.status is not PENDING: {}", entity.getStatus());
            return false;
        }
        // Basic schedule sanity check: cron-like schedules usually have at least 5 space-separated fields
        String schedule = entity.getSchedule().trim();
        String[] parts = schedule.split("\\s+");
        if (parts.length < 5) {
            logger.warn("IngestionJob.schedule appears invalid: {}", schedule);
            return false;
        }
        return true;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob entity = context.entity();

        // Set status to RUNNING and initialize runtime fields.
        logger.info("Valid IngestionJob found (jobId={}). Setting status to RUNNING and initializing runtime fields.", entity.getJobId());
        entity.setStatus("RUNNING");

        // Ensure startedAt is set (required by entity.isValid()). Use ISO-8601 representation.
        if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
            entity.setStartedAt(Instant.now().toString());
        }

        // Initialize processedCount if missing
        if (entity.getProcessedCount() == null) {
            entity.setProcessedCount(0);
        }

        // Clear previous error summary when starting a fresh run
        entity.setErrorSummary(null);
        // finishedAt should be cleared when run starts
        entity.setFinishedAt(null);

        return entity;
    }
}