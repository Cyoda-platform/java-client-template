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

/**
 * SubscriberBounceProcessor - Handles email delivery failures
 * 
 * Input: Subscriber entity in ACTIVE state with bounce notification
 * Purpose: Handle email delivery failures
 * Output: Subscriber entity in BOUNCED state
 */
@Component
public class SubscriberBounceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberBounceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberBounceProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber bounce for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscriber.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subscriber for bounce processing")
                .map(this::processSubscriberBounce)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for bounce processing
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Subscriber> entityWithMetadata) {
        Subscriber subscriber = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        return subscriber != null && 
               subscriber.isValid() && 
               "active".equalsIgnoreCase(currentState) &&
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for subscriber bounce processing
     */
    private EntityWithMetadata<Subscriber> processSubscriberBounce(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber subscriber = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing bounce for subscriber: {} in state: {}", subscriber.getEmail(), currentState);

        // Verify subscriber is in ACTIVE state
        if (!"active".equalsIgnoreCase(currentState)) {
            logger.warn("Subscriber {} is not in ACTIVE state, current state: {}", 
                       subscriber.getEmail(), currentState);
            return entityWithMetadata;
        }

        // Update subscriber for bounce
        subscriber.setIsActive(false);
        
        logger.info("Subscriber {} marked as bounced", subscriber.getEmail());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }
}
