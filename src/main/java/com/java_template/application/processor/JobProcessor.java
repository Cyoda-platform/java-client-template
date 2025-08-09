package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1000.Job;
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
import java.time.OffsetDateTime;

@Component
public class JobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobProcessor.class);
    private final ProcessorSerializer serializer;

    public JobProcessor(SerializerFactory serializerFactory) {
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
        return "JobProcessor".equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Implement business logic for Job lifecycle transitions
        String status = job.getStatus();
        if (status == null) {
            job.setStatus("SCHEDULED");
            job.setCreatedAt(OffsetDateTime.now());
            logger.info("Job status set to SCHEDULED");
        } else {
            switch (status) {
                case "SCHEDULED":
                    job.setStatus("INGESTING");
                    logger.info("Job status changed from SCHEDULED to INGESTING");
                    break;
                case "INGESTING":
                    // status should be updated externally to SUCCEEDED or FAILED after ingestion
                    logger.info("Job is currently INGESTING");
                    break;
                case "SUCCEEDED":
                case "FAILED":
                    job.setStatus("NOTIFIED_SUBSCRIBERS");
                    job.setCompletedAt(OffsetDateTime.now());
                    logger.info("Job status changed to NOTIFIED_SUBSCRIBERS");
                    break;
                case "NOTIFIED_SUBSCRIBERS":
                    // terminal state
                    logger.info("Job processing completed with status NOTIFIED_SUBSCRIBERS");
                    break;
                default:
                    logger.warn("Unknown job status: {}", status);
                    break;
            }
        }
        return job;
    }
}
