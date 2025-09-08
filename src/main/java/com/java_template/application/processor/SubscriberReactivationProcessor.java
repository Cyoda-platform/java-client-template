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
 * SubscriberReactivationProcessor - Reactivates bounced subscribers
 * 
 * Input: Subscriber entity in BOUNCED state
 * Purpose: Reactivate bounced subscriber
 * Output: Subscriber entity in ACTIVE state
 */
@Component
public class SubscriberReactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberReactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberReactivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber reactivation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscriber.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subscriber for reactivation")
                .map(this::processSubscriberReactivation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for reactivation
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Subscriber> entityWithMetadata) {
        Subscriber subscriber = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        return subscriber != null && 
               subscriber.isValid() && 
               "bounced".equalsIgnoreCase(currentState) &&
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for subscriber reactivation
     */
    private EntityWithMetadata<Subscriber> processSubscriberReactivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber subscriber = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Reactivating subscriber: {} in state: {}", subscriber.getEmail(), currentState);

        // Verify subscriber is in BOUNCED state
        if (!"bounced".equalsIgnoreCase(currentState)) {
            logger.warn("Subscriber {} is not in BOUNCED state, current state: {}", 
                       subscriber.getEmail(), currentState);
            return entityWithMetadata;
        }

        // Update subscriber for reactivation
        subscriber.setIsActive(true);
        
        logger.info("Subscriber {} reactivated successfully", subscriber.getEmail());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }
}
