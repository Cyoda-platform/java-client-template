package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JobValidator implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobValidator.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobValidator(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state or missing required fields")
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
        if (job.getJobName() == null || job.getJobName().isEmpty()) {
            logger.error("JobName is null or empty");
            return false;
        }
        if (job.getStatus() == null || job.getStatus().isEmpty()) {
            logger.error("Status is null or empty");
            return false;
        }
        if (job.getTriggerTime() == null || job.getTriggerTime().isEmpty()) {
            logger.error("TriggerTime is null or empty");
            return false;
        }
        // Additional validation can be added here
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Business logic: Transition status from SCHEDULED to INGESTING
        if ("scheduled".equalsIgnoreCase(job.getStatus())) {
            logger.info("Updating job status from SCHEDULED to INGESTING");
            job.setStatus("INGESTING");
        }
        // Additional processing logic like triggering ingestion can be implemented elsewhere
        return job;
    }
}
