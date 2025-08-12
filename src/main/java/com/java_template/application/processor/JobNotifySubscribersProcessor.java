package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class JobNotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobNotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public JobNotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for notification request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for notification")
            .map(context1 -> {
                Job job = context1.entity();
                try {
                    notifySubscribers(job);
                } catch (Exception e) {
                    logger.error("Error notifying subscribers for job: {}", job.getJobName(), e);
                    // We do not fail the processor, just log the error
                }
                // Update job state to NOTIFIED_SUBSCRIBERS and set completedAt timestamp if not already set
                job.setStatus("NOTIFIED_SUBSCRIBERS");
                if (job.getCompletedAt() == null) {
                    job.setCompletedAt(Instant.now().toString());
                }
                return job;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        // Valid states to notify subscribers: SUCCEEDED or FAILED
        return entity != null && ("SUCCEEDED".equalsIgnoreCase(entity.getStatus()) || "FAILED".equalsIgnoreCase(entity.getStatus()));
    }

    private void notifySubscribers(Job job) throws Exception {
        // Fetch all active subscribers
        CompletableFuture<ArrayNode> futureSubscribers = entityService.getItemsByCondition(
            Subscriber.ENTITY_NAME,
            String.valueOf(Subscriber.ENTITY_VERSION),
            entityService.getSearchConditionRequest().group("AND",
                entityService.getCondition().of("$.active", "EQUALS", true)
            ),
            true
        );

        ArrayNode subscribers = futureSubscribers.get();
        logger.info("Found {} active subscribers to notify", subscribers.size());

        for (JsonNode subNode : subscribers) {
            Subscriber subscriber = entityService.getObjectMapper().treeToValue(subNode, Subscriber.class);
            sendNotification(subscriber, job);
        }
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        // For now, just log the notification attempt
        logger.info("Sending notification to subscriber: {} via {}", subscriber.getContactValue(), subscriber.getContactType());
        // TODO: Implement actual notification sending logic (email, webhook, etc.)
    }
}
