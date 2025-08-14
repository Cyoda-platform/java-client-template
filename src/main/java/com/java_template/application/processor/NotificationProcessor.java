package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class NotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public NotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing notification for job completion, request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .validate(this::isValidEntity, "Invalid job entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getJobName() != null && !entity.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();

        try {
            // Fetch active subscribers
            Condition activeCondition = Condition.of("$.active", "EQUALS", true);
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", activeCondition);
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    searchCondition,
                    true
            );

            ArrayList<Subscriber> activeSubscribers = new ArrayList<>();
            ArrayNode subscribersArray = subscribersFuture.get();
            for (JsonNode subscriberNode : subscribersArray) {
                Subscriber subscriber = context.serializer().toEntity(subscriberNode.toString(), Subscriber.class);
                if (subscriber != null) {
                    activeSubscribers.add(subscriber);
                }
            }

            // Send notifications asynchronously
            for (Subscriber subscriber : activeSubscribers) {
                sendNotification(entity, subscriber);
            }

            entity.setStatus("NOTIFIED_SUBSCRIBERS");
            entity.setCompletedAt(Instant.now().toString());
            logger.info("All active subscribers notified for job: {}", entity.getJobName());

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to notify subscribers for job: {}, error: {}", entity.getJobName(), e.getMessage());
        }

        return entity;
    }

    private void sendNotification(Job job, Subscriber subscriber) {
        // Implement notification logic based on contactType
        // Example: log notification event or simulate sending
        logger.info("Sending notification to subscriber: {} via {} with details: {}",
                subscriber.getSubscriberName(), subscriber.getContactType(), subscriber.getContactDetails());
        // Actual notification sending (email, webhook, etc.) would be implemented here asynchronously
    }
}
