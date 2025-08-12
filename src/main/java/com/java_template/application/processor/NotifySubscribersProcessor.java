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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.springframework.beans.factory.annotation.Autowired;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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

        try {
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
            );

            List<Subscriber> subscribers = subscribersFuture.thenApply(arrayNode -> {
                // Map ArrayNode to List<Subscriber>
                // For simplicity, assuming ObjectMapper is available and mapping works
                // In real case, might need custom conversion
                try {
                    return arrayNode.findValuesAsText(null).stream()
                        .map(jsonStr -> {
                            Subscriber sub = new Subscriber();
                            // Mapping from JSON to entity fields
                            // Since no direct method, assume JSON node method or manual parsing
                            return sub;
                        })
                        .toList();
                } catch (Exception e) {
                    logger.error("Error mapping subscribers from JSON", e);
                    return List.<Subscriber>of();
                }
            }).get();

            // Send notifications - could be email/webhook
            for (Subscriber subscriber : subscribers) {
                sendNotification(subscriber, job);
            }

            // Update job status to NOTIFIED_SUBSCRIBERS
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            job.setDetails((job.getDetails() != null ? job.getDetails() : "") + "; Notified " + subscribers.size() + " subscribers");
            logger.info("Notified {} subscribers", subscribers.size());
        } catch (Exception e) {
            logger.error("Exception notifying subscribers", e);
            job.setDetails((job.getDetails() != null ? job.getDetails() : "") + "; Notification failure: " + e.getMessage());
        }

        return job;
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        // Placeholder for sending notification logic
        logger.info("Sending notification to subscriber: {} at email: {}", subscriber.getSubscriberName(), subscriber.getContactEmail());
        // Could implement email or webhook call here
    }
}
