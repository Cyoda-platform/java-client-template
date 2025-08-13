package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;
import java.util.List;

@Component
public class SubscriberNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public SubscriberNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing subscriber notifications for job request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job entity for notification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        if (job == null) {
            logger.error("Job entity is null");
            return false;
        }
        if (job.getStatus() == null || job.getStatus().isEmpty()) {
            logger.error("Job status is null or empty");
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Business logic: Notify all active subscribers asynchronously
        logger.info("Notifying subscribers for job: {} with status: {}", job.getJobName(), job.getStatus());

        // Build search condition for active subscribers
        SearchConditionRequest condition = SearchConditionRequest.group(
            "AND",
            Condition.of("$.active", "EQUALS", true)
        );

        CompletableFuture<ArrayNode> activeSubscribersFuture = entityService.getItemsByCondition(
            Subscriber.ENTITY_NAME,
            String.valueOf(Subscriber.ENTITY_VERSION),
            condition,
            true
        );

        activeSubscribersFuture.thenAccept(activeSubscribers -> {
            for (int i = 0; i < activeSubscribers.size(); i++) {
                ObjectNode subscriberNode = (ObjectNode) activeSubscribers.get(i);
                String email = subscriberNode.path("contactEmail").asText(null);
                if (email != null) {
                    // Simulate sending notification asynchronously
                    logger.info("Sending notification to subscriber email: {}", email);
                    // TODO: Integrate real email sending service or webhook
                }
            }
            // Update job status to NOTIFIED_SUBSCRIBERS after notifications
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            logger.info("All active subscribers notified for job: {}", job.getJobName());
        }).exceptionally(ex -> {
            logger.error("Failed to notify subscribers: {}", ex.getMessage());
            return null;
        });

        return job;
    }
}
