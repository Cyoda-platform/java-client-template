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

@Component
public class IngestNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestNobelLaureatesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IngestNobelLaureatesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity for ingestion")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        if (job == null) {
            logger.error("Job entity is null");
            return false;
        }
        // Additional validation can be done here, e.g., check jobName and scheduledTime
        if (job.getJobName() == null || job.getJobName().isEmpty()) {
            logger.error("Job name is missing");
            return false;
        }
        if (job.getScheduledTime() == null || job.getScheduledTime().isEmpty()) {
            logger.error("Scheduled time is missing");
            return false;
        }
        // Could add ISO 8601 timestamp format validation if needed
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        logger.info("Starting ingestion process for job: {}", job.getJobName());
        // TODO: Implement ingestion logic
        // - Transition jobStatus to "INGESTING"
        // - Fetch Nobel laureates data from OpenDataSoft API
        // - For each laureate, save a new Laureate entity (triggering processLaureate())
        // - On success, update jobStatus to "SUCCEEDED"
        // - On failure, update jobStatus to "FAILED"
        // - Save result summary in job
        return job;
    }
}}
