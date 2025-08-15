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

import java.time.Instant;
import java.util.ArrayList;
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
        logger.info("Processing NotifySubscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null && !job.getTechnicalId().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Simplified notify implementation: discover active subscribers and record notification attempt results.
        try {
            logger.info("Notifying subscribers for job: {}", job.getTechnicalId());

            // In a real implementation, query repository for active subscribers and filter by preferences
            List<Subscriber> activeSubscribers = new ArrayList<>();

            for (Subscriber s : activeSubscribers) {
                // Build payload based on subscriber preferences - simplified to a short summary
                String payload = String.format("Job %s finished with status %s", job.getTechnicalId(), job.getStatus());
                // Delegate to DeliveryProcessor in the real flow. Here we just log and simulate success.
                logger.info("Delivering payload to subscriber {}: {}", s.getTechnicalId(), payload);
                s.setLastNotifiedAt(Instant.now().toString());
                s.setLastNotificationStatus("DELIVERED");
                // Persist subscriber changes in repository in a real implementation
            }

            job.setStatus("NOTIFIED_SUBSCRIBERS");

        } catch (Exception e) {
            logger.error("Error while notifying subscribers for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
        }

        return job;
    }
}
