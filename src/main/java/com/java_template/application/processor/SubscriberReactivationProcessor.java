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

import java.util.HashMap;
import java.util.Map;

/**
 * Processor for subscriber reactivation workflow transition.
 * Handles the reactivate transition (suspended → active).
 * 
 * Business Logic:
 * - Clears suspension reason and date
 * - Sets reactivation date to current timestamp
 * - Logs reactivation event
 */
@Component
public class SubscriberReactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberReactivationProcessor.class);
    private final ProcessorSerializer serializer;

    public SubscriberReactivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("SubscriberReactivationProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing subscriber reactivation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::validateReactivationData, "Invalid reactivation data")
            .map(ctx -> {
                Subscriber subscriber = ctx.entity();
                
                // Get preferences or initialize
                Map<String, Object> preferences = subscriber.getPreferences();
                if (preferences == null) {
                    preferences = new HashMap<>();
                    subscriber.setPreferences(preferences);
                }
                
                // Clear suspension-related data
                preferences.remove("suspensionReason");
                preferences.remove("lastSuspensionDate");
                
                // Set reactivation data
                preferences.put("reactivationDate", java.time.LocalDateTime.now().toString());
                preferences.put("reactivationReason", "manual_reactivation");
                
                // Ensure subscriber is active
                subscriber.setIsActive(true);
                
                logger.info("Subscriber reactivated: {}", subscriber.getEmail());
                
                return subscriber;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "SubscriberReactivationProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates reactivation data.
     * 
     * @param subscriber The subscriber to validate
     * @return true if valid, false otherwise
     */
    private boolean validateReactivationData(Subscriber subscriber) {
        // Subscriber must exist
        if (subscriber == null) {
            logger.warn("Reactivation failed: Subscriber is null");
            return false;
        }
        
        // Email must be set
        if (subscriber.getEmail() == null || subscriber.getEmail().trim().isEmpty()) {
            logger.warn("Reactivation failed: Email is required");
            return false;
        }
        
        // Subscriber should have preferences (indicating previous suspension)
        if (subscriber.getPreferences() == null) {
            logger.warn("Reactivation failed: No preferences found for subscriber: {}", subscriber.getEmail());
            return false;
        }
        
        // Check if subscriber was previously suspended
        Map<String, Object> preferences = subscriber.getPreferences();
        if (!preferences.containsKey("suspensionReason") && !preferences.containsKey("lastSuspensionDate")) {
            logger.warn("Reactivation failed: No suspension history found for subscriber: {}", subscriber.getEmail());
            return false;
        }
        
        logger.debug("Reactivation data validation passed for email: {}", subscriber.getEmail());
        return true;
    }
}
