package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job notification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(ctx -> processNotification(ctx.entity()))
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getJobStatus() != null && !job.getJobStatus().isEmpty();
    }

    private Job processNotification(Job job) {
        try {
            // Fetch all active subscribers
            CompletableFuture<ArrayNode> futureSubscribers = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                SearchConditionRequest.group("AND",
                    Condition.of("$.active", "EQUALS", true)
                ),
                true
            );

            ArrayNode subscribersNode = futureSubscribers.get();
            List<Subscriber> subscribersToNotify = new ArrayList<>();
            for (int i = 0; i < subscribersNode.size(); i++) {
                Subscriber subscriber = serializer.getObjectMapper().treeToValue(subscribersNode.get(i), Subscriber.class);
                if (subscriber != null) {
                    if (job.getResultSummary() != null && subscriber.getPreferredCategories() != null && !subscriber.getPreferredCategories().isEmpty()) {
                        // Filter subscribers by preferred categories if set
                        boolean toNotify = false;
                        String[] preferredCategories = subscriber.getPreferredCategories().split(",");
                        for (String category : preferredCategories) {
                            if (job.getResultSummary().contains(category.trim())) {
                                toNotify = true;
                                break;
                            }
                        }
                        if (toNotify) {
                            subscribersToNotify.add(subscriber);
                        }
                    } else {
                        // If no preferred categories, notify anyway
                        subscribersToNotify.add(subscriber);
                    }
                }
            }

            // Simulate notification sending
            for (Subscriber subscriber : subscribersToNotify) {
                logger.info("Notifying subscriber: {} via {}", subscriber.getContactDetails(), subscriber.getContactType());
                // TODO: Implement actual notification sending (email, webhook, etc.)
            }

            job.setJobStatus("NOTIFIED_SUBSCRIBERS");

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception during notifying subscribers", e);
            job.setJobStatus("FAILED");
        }

        return job;
    }
}