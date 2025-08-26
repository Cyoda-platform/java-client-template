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
import java.time.format.DateTimeFormatter;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartIngestionProcessor(SerializerFactory serializerFactory) {
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

        // Set start time to now (ISO-8601) and transition state to INGESTING
        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        entity.setStartedAt(now);
        entity.setStatus("INGESTING");

        // Initialize or reset ingestion summary counters
        Job.IngestionSummary summary = entity.getIngestionSummary();
        if (summary == null) {
            summary = new Job.IngestionSummary();
        }
        if (summary.getRecordsFetched() == null) summary.setRecordsFetched(0);
        if (summary.getRecordsProcessed() == null) summary.setRecordsProcessed(0);
        if (summary.getRecordsFailed() == null) summary.setRecordsFailed(0);
        entity.setIngestionSummary(summary);

        // Clear previous error details on new ingestion start
        entity.setErrorDetails(null);

        logger.info("Job {} moved to INGESTING at {}", entity.getTechnicalId(), entity.getStartedAt());

        // NOTE: Emitting subsequent fetch/process events is handled by the workflow engine (Cyoda).
        // This processor only adjusts the Job entity state and prepares ingestion metadata.

        return entity;
    }
}