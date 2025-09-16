package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
 * Processor for subscriber unsubscribe workflow transition.
 * Handles the unsubscribe transition (active → unsubscribed).
 * 
 * Business Logic:
 * - Validates unsubscribe token
 * - Sets isActive to false
 * - Logs unsubscribe event with reason (if provided)
 */
@Component
public class SubscriberUnsubscribeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberUnsubscribeProcessor.class);
    private final ProcessorSerializer serializer;

    public SubscriberUnsubscribeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("SubscriberUnsubscribeProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing subscriber unsubscribe for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::validateUnsubscribeData, "Invalid unsubscribe data")
            .map(ctx -> {
                Subscriber subscriber = ctx.entity();
                
                // Set isActive to false
                subscriber.setIsActive(false);
                
                // Log unsubscribe event
                logger.info("Subscriber unsubscribed: {} (token: {})", 
                           subscriber.getEmail(), 
                           subscriber.getUnsubscribeToken());
                
                return subscriber;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "SubscriberUnsubscribeProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates unsubscribe data.
     * 
     * @param subscriber The subscriber to validate
     * @return true if valid, false otherwise
     */
    private boolean validateUnsubscribeData(Subscriber subscriber) {
        // Subscriber must exist
        if (subscriber == null) {
            logger.warn("Unsubscribe failed: Subscriber is null");
            return false;
        }
        
        // Email must be set
        if (subscriber.getEmail() == null || subscriber.getEmail().trim().isEmpty()) {
            logger.warn("Unsubscribe failed: Email is required");
            return false;
        }
        
        // Unsubscribe token must be set and valid
        if (subscriber.getUnsubscribeToken() == null || subscriber.getUnsubscribeToken().trim().isEmpty()) {
            logger.warn("Unsubscribe failed: Unsubscribe token is required");
            return false;
        }
        
        // Subscriber should currently be active
        if (subscriber.getIsActive() == null || !subscriber.getIsActive()) {
            logger.warn("Unsubscribe failed: Subscriber is not active: {}", subscriber.getEmail());
            return false;
        }
        
        logger.debug("Unsubscribe data validation passed for email: {}", subscriber.getEmail());
        return true;
    }
}
