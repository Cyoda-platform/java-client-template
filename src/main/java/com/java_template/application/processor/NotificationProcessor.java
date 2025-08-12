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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Component
public class NotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Notifying subscribers for Job: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob, "Invalid Job entity state for notification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Query subscribers interested in the ingested categories
            List<Subscriber> subscribers = querySubscribers(job);

            // Send notifications to subscribers
            for (Subscriber subscriber : subscribers) {
                logger.info("Notifying subscriber: {} via email: {}", subscriber.getSubscriberName(), subscriber.getContactEmail());
                // TODO: Implement actual notification sending logic (email, webhook)
            }

            // Update Job status to NOTIFIED_SUBSCRIBERS
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            logger.info("Job status updated to NOTIFIED_SUBSCRIBERS: {}", job.getJobName());

        } catch (Exception e) {
            logger.error("Error sending notifications for Job {}: {}", job.getJobName(), e.getMessage());
        }
        return job;
    }

    private List<Subscriber> querySubscribers(Job job) {
        // TODO: Implement querying subscribers by job's laureate categories
        // Return empty list for now
        return java.util.Collections.emptyList();
    }
}
