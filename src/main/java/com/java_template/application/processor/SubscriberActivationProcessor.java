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

/**
 * SubscriberActivationProcessor - Handles activation of pending subscribers
 * 
 * Input: Subscriber entity in PENDING state
 * Purpose: Activate confirmed subscriber
 * Output: Subscriber entity in ACTIVE state
 */
@Component
public class SubscriberActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber activation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscriber.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subscriber for activation")
                .map(this::processSubscriberActivation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for activation
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Subscriber> entityWithMetadata) {
        Subscriber subscriber = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        return subscriber != null && 
               subscriber.isValid() && 
               "pending".equalsIgnoreCase(currentState) &&
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for subscriber activation
     */
    private EntityWithMetadata<Subscriber> processSubscriberActivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber subscriber = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Activating subscriber: {} in state: {}", subscriber.getEmail(), currentState);

        // Verify subscriber is in PENDING state
        if (!"pending".equalsIgnoreCase(currentState)) {
            logger.warn("Subscriber {} is not in PENDING state, current state: {}", 
                       subscriber.getEmail(), currentState);
            return entityWithMetadata;
        }

        // Update subscriber for activation
        subscriber.setIsActive(true);
        
        logger.info("Subscriber {} activated successfully", subscriber.getEmail());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }
}
