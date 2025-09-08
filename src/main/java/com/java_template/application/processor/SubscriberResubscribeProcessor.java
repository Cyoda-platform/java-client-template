package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SubscriberResubscribeProcessor - Handles subscriber resubscription
 * 
 * Input: Subscriber entity in UNSUBSCRIBED state
 * Purpose: Handle subscriber resubscription
 * Output: Subscriber entity in ACTIVE state
 */
@Component
public class SubscriberResubscribeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberResubscribeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberResubscribeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber resubscribe for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscriber.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subscriber for resubscribe")
                .map(this::processSubscriberResubscribe)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for resubscribe
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Subscriber> entityWithMetadata) {
        Subscriber subscriber = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        return subscriber != null && 
               subscriber.isValid() && 
               "unsubscribed".equalsIgnoreCase(currentState) &&
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for subscriber resubscription
     */
    private EntityWithMetadata<Subscriber> processSubscriberResubscribe(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber subscriber = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Resubscribing subscriber: {} in state: {}", subscriber.getEmail(), currentState);

        // Verify subscriber is in UNSUBSCRIBED state
        if (!"unsubscribed".equalsIgnoreCase(currentState)) {
            logger.warn("Subscriber {} is not in UNSUBSCRIBED state, current state: {}", 
                       subscriber.getEmail(), currentState);
            return entityWithMetadata;
        }

        // Update subscriber for resubscription
        subscriber.setIsActive(true);
        subscriber.setSubscriptionDate(LocalDateTime.now());
        
        // Generate new unsubscribe token for security
        subscriber.setUnsubscribeToken(UUID.randomUUID().toString());
        
        logger.info("Subscriber {} resubscribed successfully", subscriber.getEmail());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }
}
