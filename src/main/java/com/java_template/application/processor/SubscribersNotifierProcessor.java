package com.java_template.application.processor;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Subscriber;
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
import java.util.concurrent.CompletableFuture;

@Component
public class SubscribersNotifierProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public SubscribersNotifierProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Notifying Subscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            CompletableFuture<List<Subscriber>> subscribersFuture = entityService.getItemsByCondition(Subscriber.ENTITY_NAME, com.java_template.common.config.Config.ENTITY_VERSION, new SubscriberActiveCondition(), true);
            List<Subscriber> subscribers = subscribersFuture.get();

            for (Subscriber subscriber : subscribers) {
                if (subscriber.isActive()) {
                    // Notify subscriber by email/webhook
                    logger.info("Notifying subscriber {} at email {} with webhook {}", subscriber.getId(), subscriber.getContactEmail(), subscriber.getWebhookUrl());
                    // Implementation of notification sending omitted
                }
            }

            return job;
        } catch (Exception e) {
            logger.error("Failed to notify subscribers: {}", e.getMessage());
            return job;
        }
    }

    private static class SubscriberActiveCondition {
        public boolean matches(Subscriber subscriber) {
            return subscriber.isActive();
        }
    }
}
