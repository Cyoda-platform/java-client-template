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
        logger.info("Processing notifications for Job request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .validate(this::isValidEntity, "Invalid job state for notification processing")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Simulate notification dispatch to active subscribers
        try {
            List<Subscriber> activeSubscribers = fetchActiveSubscribers();
            for (Subscriber subscriber : activeSubscribers) {
                sendNotification(subscriber, job);
            }
            logger.info("Notifications sent to {} subscribers for job {}", activeSubscribers.size(), job.getJobName());
        } catch (Exception e) {
            logger.error("Failed to send notifications for job {}: {}", job.getJobName(), e.getMessage());
        }
        return job;
    }

    private List<Subscriber> fetchActiveSubscribers() {
        // Placeholder: fetch active subscribers from data source
        // For now, return empty list
        return List.of();
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        // Placeholder for notification sending logic
        logger.info("Sending notification to {} of type {} for job {}", subscriber.getContactValue(), subscriber.getContactType(), job.getJobName());
    }
}
