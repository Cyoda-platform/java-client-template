package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class SubscribersNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscribersNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public SubscribersNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Starting notification process for Job completion: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob, "Invalid job state")
            .map(this::notifySubscribers)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getStatus() != null && (job.getStatus().equalsIgnoreCase("SUCCEEDED") || job.getStatus().equalsIgnoreCase("FAILED"));
    }

    private Job notifySubscribers(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Fetch active subscribers
        try {
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.active", "EQUALS", "true")
            );

            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            );

            List<Subscriber> subscribers = subscribersFuture.thenApply(arrayNode -> {
                return arrayNode.findValuesAsText("").stream()
                    .map(node -> {
                        try {
                            return entityService.getObjectMapper().treeToValue(node, Subscriber.class);
                        } catch (Exception e) {
                            logger.error("Failed to deserialize subscriber", e);
                            return null;
                        }
                    })
                    .filter(s -> s != null)
                    .toList();
            }).get();

            logger.info("Fetched {} active subscribers", subscribers.size());

            // Simulate sending notification to each subscriber
            for (Subscriber subscriber : subscribers) {
                logger.info("Notifying subscriber: {} at {}", subscriber.getContactType(), subscriber.getContactAddress());
                // Here you would implement actual notification sending (email, webhook, etc.)
            }

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to fetch or notify subscribers", e);
            job.setStatus("FAILED");
            job.setFinishedAt(OffsetDateTime.now());
            return job;
        }

        // Update job status to NOTIFIED_SUBSCRIBERS
        job.setStatus("NOTIFIED_SUBSCRIBERS");
        return job;
    }
}
