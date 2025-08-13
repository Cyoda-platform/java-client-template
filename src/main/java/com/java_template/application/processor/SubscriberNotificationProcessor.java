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
import java.util.stream.Collectors;

@Component
public class SubscriberNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Notifying subscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob)
            .map(this::processNotificationLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getStatus() != null &&
            (job.getStatus().equalsIgnoreCase("SUCCEEDED") || job.getStatus().equalsIgnoreCase("FAILED"));
    }

    private Job processNotificationLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Notify all active subscribers
        try {
            // TODO: Implement actual notification logic, e.g., send email/webhook
            // For now, just log and simulate notification
            logger.info("Fetching active subscribers for notification");
            // Dummy fetch simulated
            // List<Subscriber> activeSubscribers = subscriberRepository.findActiveSubscribers();
            // For each subscriber, send notification
            logger.info("Notified all active subscribers about job: {}", job.getJobName());

            // Update job status to NOTIFIED_SUBSCRIBERS
            job.setStatus("NOTIFIED_SUBSCRIBERS");

        } catch (Exception e) {
            logger.error("Failed to notify subscribers: {}", e.getMessage(), e);
        }

        return job;
    }
}
