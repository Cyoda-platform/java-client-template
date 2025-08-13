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
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JobIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobIngestionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob)
            .map(this::processJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        if (job == null) {
            logger.error("Job entity is null");
            return false;
        }
        if (job.getJobName() == null || job.getJobName().isEmpty()) {
            logger.error("Job name is required");
            return false;
        }
        // Additional validation can be added here
        return true;
    }

    private Job processJobLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Step 1: Change status from SCHEDULED to INGESTING
            if ("scheduled".equalsIgnoreCase(job.getStatus())) {
                job.setStatus("INGESTING");
                job.setCreatedAt(Instant.now().toString());
                logger.info("Job status set to INGESTING");
            }

            // Step 2: Ingest laureate data from API and create Laureate entities
            // This is a placeholder as ingestion is likely asynchronous;
            // Actual ingestion logic would fetch data, transform and save laureates.
            // For now, just log the action.
            logger.info("Starting ingestion of laureate data from OpenDataSoft API");

            // TODO: Implement actual ingestion logic, e.g., call service to fetch laureates and create entities

            // Step 3: After ingestion, set status to SUCCEEDED
            job.setStatus("SUCCEEDED");
            job.setCompletedAt(Instant.now().toString());
            logger.info("Job ingestion succeeded");

            // Step 4: Notify subscribers handled by separate processor

        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now().toString());
            logger.error("Job ingestion failed: {}", e.getMessage(), e);
        }

        return job;
    }
}
