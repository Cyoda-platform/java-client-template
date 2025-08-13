package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    public NotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        // Fetch active subscribers from entityService
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

    private List<Subscriber> fetchActiveSubscribers() throws ExecutionException, InterruptedException {
        // Fetch all subscribers (filtering for active if needed)
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
        );
        ArrayNode subscribersArray = itemsFuture.get();
        List<Subscriber> activeSubscribers = new ArrayList<>();
        for (JsonNode node : subscribersArray) {
            Subscriber subscriber = serializer.toEntity(node, Subscriber.class);
            if (subscriber != null && subscriber.isValid()) {
                activeSubscribers.add(subscriber);
            }
        }
        return activeSubscribers;
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        // Simulated notification logic
        logger.info("Sending notification to '{}' of type '{}' for job '{}'",
                subscriber.getContactValue(), subscriber.getContactType(), job.getJobName());
        // Here you could integrate with email service, webhook call, etc.
    }
}
