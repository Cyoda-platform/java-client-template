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
        logger.info("Notifying subscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity")
            .map(this::notifySubscribers)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && ("SUCCEEDED".equals(job.getStatus()) || "FAILED".equals(job.getStatus()));
    }

    private Job notifySubscribers(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Simulate fetching active subscribers and notification
        logger.info("Fetching active subscribers for notification");

        // Here, we would query the Subscriber repository to get active subscribers
        // For demonstration, simulate with dummy list
        List<Subscriber> activeSubscribers = List.of(); // TODO: Replace with actual query

        if (activeSubscribers.isEmpty()) {
            logger.info("No active subscribers to notify");
        } else {
            for (Subscriber subscriber : activeSubscribers) {
                // Simulate notification logic
                logger.info("Notifying subscriber: {} via {}", subscriber.getSubscriberId(), subscriber.getContactType());
            }
        }

        job.setStatus("NOTIFIED_SUBSCRIBERS");
        return job;
    }
}
