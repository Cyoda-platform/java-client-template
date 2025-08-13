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
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        if (job == null) {
            return false;
        }
        // Validate required fields for ingestion start
        if (job.getApiUrl() == null || job.getApiUrl().isEmpty()) {
            logger.error("Job apiUrl is null or empty");
            return false;
        }
        if (!"scheduled".equalsIgnoreCase(job.getStatus())) {
            logger.error("Job status is not 'scheduled' for ingestion start");
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Implement ingestion start logic:
        // 1. Set status to 'INGESTING'
        job.setStatus("INGESTING");
        // 2. Set startedAt timestamp to current time
        job.setStartedAt(java.time.Instant.now().toString());
        // 3. Clear completedAt and message fields
        job.setCompletedAt(null);
        job.setMessage(null);
        // Additional ingestion logic (e.g. trigger async ingestion) can be added here
        logger.info("Job ingestion started for apiUrl: {}", job.getApiUrl());
        return job;
    }
}
