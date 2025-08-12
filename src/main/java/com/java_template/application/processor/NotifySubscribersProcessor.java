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
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job notify subscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job state for notification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        if (entity == null) {
            logger.error("Job entity is null in NotifySubscribersProcessor");
            return false;
        }
        String status = entity.getStatus();
        if (!"SUCCEEDED".equalsIgnoreCase(status) && !"FAILED".equalsIgnoreCase(status)) {
            logger.error("Job status is not SUCCEEDED or FAILED in NotifySubscribersProcessor");
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Fetch subscribers - Assuming a method exists to fetch all subscribers
        try {
            // Placeholder: fetch subscribers from repository or service
            List<Subscriber> subscribers = fetchSubscribers();

            // Send notifications - could be email/webhook
            for (Subscriber subscriber : subscribers) {
                sendNotification(subscriber, job);
            }

            // Update job status to NOTIFIED_SUBSCRIBERS
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            job.setDetails(job.getDetails() + "; Notified " + subscribers.size() + " subscribers");
            logger.info("Notified {} subscribers", subscribers.size());
        } catch (Exception e) {
            logger.error("Exception notifying subscribers", e);
            job.setDetails(job.getDetails() + "; Notification failure: " + e.getMessage());
        }

        return job;
    }

    private List<Subscriber> fetchSubscribers() {
        // Placeholder for subscriber fetch logic
        // In real implementation, call a repository or service
        return List.of(); // Empty list for now
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        // Placeholder for sending notification logic
        logger.info("Sending notification to subscriber: {} at email: {}", subscriber.getSubscriberName(), subscriber.getContactEmail());
        // Could implement email or webhook call here
    }
}
