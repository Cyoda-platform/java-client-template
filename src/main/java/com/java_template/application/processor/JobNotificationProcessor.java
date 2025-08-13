package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.List;

@Component
public class JobNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job notification for request: {}", request.getId());

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
        // Job must be in SUCCEEDED or FAILED state to notify subscribers
        if (job == null) {
            logger.error("Job entity is null");
            return false;
        }
        if (!"SUCCEEDED".equalsIgnoreCase(job.getStatus()) && !"FAILED".equalsIgnoreCase(job.getStatus())) {
            logger.error("Job status is not suitable for notification: {}", job.getStatus());
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Implement notification logic:
        // 1. Fetch active subscribers
        // 2. Send notifications (e.g., email, webhook) to each active subscriber
        // 3. Update job status to NOTIFIED_SUBSCRIBERS

        // Placeholder: Simulate fetching subscribers and sending notifications
        logger.info("Fetching active subscribers for notification...");
        // In real implementation, inject repository/service for subscribers

        // Simulate notification sending
        logger.info("Sending notifications for job completion status: {}", job.getStatus());

        // Update job status
        job.setStatus("NOTIFIED_SUBSCRIBERS");
        job.setCompletedAt(java.time.Instant.now().toString());

        logger.info("Job notifications sent successfully.");
        return job;
    }
}
