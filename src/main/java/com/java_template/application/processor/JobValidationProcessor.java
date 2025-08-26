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
        Job job = context.entity();
        if (job == null) return null;

        // Ensure attempts is initialized
        if (job.getAttempts() == null) {
            job.setAttempts(0);
        }

        // Basic presence checks (defensive, though validated earlier)
        String type = job.getType();
        if (type == null || type.isBlank()) {
            job.setStatus("failed");
            job.setLastError("Missing job type");
            logger.warn("Job {} validation failed: missing type", job.getId());
            return job;
        }

        if (job.getPayload() == null) {
            job.setStatus("failed");
            job.setLastError("Missing job payload");
            logger.warn("Job {} validation failed: missing payload", job.getId());
            return job;
        }

        // Type-specific validation and state transition
        if ("ingest".equalsIgnoreCase(type)) {
            Object source = job.getPayload().get("source");
            if (source == null || source.toString().isBlank()) {
                job.setStatus("failed");
                job.setLastError("Ingest job missing payload.source");
                logger.warn("Job {} validation failed: ingest payload.source missing", job.getId());
                return job;
            }
            // Valid ingest job -> move to in_progress
            job.setStatus("in_progress");
            job.setLastError(null);
            logger.info("Job {} validated as ingest and moved to in_progress", job.getId());
            return job;
        }

        if ("notify".equalsIgnoreCase(type)) {
            if (job.getSubscriberIds() == null || job.getSubscriberIds().isEmpty()) {
                job.setStatus("failed");
                job.setLastError("Notify job missing subscriberIds");
                logger.warn("Job {} validation failed: missing subscriberIds", job.getId());
                return job;
            }
            if (job.getPetIds() == null || job.getPetIds().isEmpty()) {
                job.setStatus("failed");
                job.setLastError("Notify job missing petIds");
                logger.warn("Job {} validation failed: missing petIds", job.getId());
                return job;
            }
            // Valid notify job -> move to in_progress
            job.setStatus("in_progress");
            job.setLastError(null);
            logger.info("Job {} validated as notify and moved to in_progress", job.getId());
            return job;
        }

        // Unknown job type
        job.setStatus("failed");
        job.setLastError("Unknown job type: " + type);
        logger.warn("Job {} validation failed: unknown type {}", job.getId(), type);
        return job;
    }
}