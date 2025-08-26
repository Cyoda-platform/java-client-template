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
public class PersistResultsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistResultsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistResultsProcessor(SerializerFactory serializerFactory) {
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
        Job job = context.entity();

        // Business logic:
        // - If resultSummary is missing or blank, mark job as FAILED and increment retryCount.
        // - Otherwise mark job as COMPLETED.
        // - Always update lastRunAt to current timestamp.
        // - Do not perform any add/update/delete via EntityService on this Job entity; modifications here will be persisted by Cyoda automatically.

        String summary = job.getResultSummary();
        boolean hasSummary = summary != null && !summary.isBlank();

        // Update last run timestamp
        job.setLastRunAt(Instant.now().toString());

        if (hasSummary) {
            job.setStatus("COMPLETED");
            logger.info("Job {} marked as COMPLETED. resultSummary={}", job.getJobId(), summary);
        } else {
            // resultSummary missing -> failure condition
            // Increment retryCount safely (entity.isValid ensured retryCount != null)
            Integer currentRetries = job.getRetryCount();
            if (currentRetries == null) {
                currentRetries = 0;
            }
            job.setRetryCount(currentRetries + 1);
            job.setStatus("FAILED");
            logger.warn("Job {} marked as FAILED. retryCount={}", job.getJobId(), job.getRetryCount());
        }

        return job;
    }
}