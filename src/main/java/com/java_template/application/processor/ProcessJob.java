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
public class ProcessJob implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessJob.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessJob(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

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

    private boolean isValidEntity(Job entity) {
        if (entity == null) return false;
        String status = entity.getStatus();
        // Status must be one of the defined workflow states
        return status != null && (status.equalsIgnoreCase("pending") || status.equalsIgnoreCase("ingesting") || status.equalsIgnoreCase("succeeded") || status.equalsIgnoreCase("failed") || status.equalsIgnoreCase("notified_subscribers"));
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        String status = job.getStatus();
        logger.info("Current Job status: {}", status);

        switch (status.toLowerCase()) {
            case "pending":
                logger.info("Starting ingestion for Job: {}", job.getJobName());
                job.setStatus("ingesting");
                // Additional logic to initiate ingestion can be added here
                break;
            case "ingesting":
                // Ingestion processing logic can be placed here
                // For example, fetching data and updating job status accordingly
                // This processor is triggered on ingestion_succeeded or ingestion_failed transitions
                logger.info("Ingestion in progress for Job: {}", job.getJobName());
                break;
            case "succeeded":
                logger.info("Job ingestion succeeded for: {}", job.getJobName());
                break;
            case "failed":
                logger.warn("Job ingestion failed for: {}", job.getJobName());
                break;
            case "notified_subscribers":
                logger.info("Subscribers notified for Job: {}", job.getJobName());
                break;
            default:
                logger.warn("Unknown Job status: {}", status);
        }

        return job;
    }
}
