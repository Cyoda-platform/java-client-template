package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
import java.util.UUID;

/**
 * Processor for subscriber suspension workflow transition.
 * Handles the suspend transition (active → suspended).
 * 
 * Business Logic:
 * - Sets suspension reason (bounce, complaint, etc.)
 * - Increments suspension count
 * - If suspension count > 3, transitions to unsubscribed
 * - Logs suspension event
 */
@Component
public class SubscriberSuspensionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberSuspensionProcessor.class);
    private static final int MAX_SUSPENSION_COUNT = 3;
    
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SubscriberSuspensionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.debug("SubscriberSuspensionProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing subscriber suspension for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::validateSuspensionData, "Invalid suspension data")
            .map(ctx -> {
                Subscriber subscriber = ctx.entity();
                
                // Get current suspension count from preferences or initialize
                Map<String, Object> preferences = subscriber.getPreferences();
                if (preferences == null) {
                    preferences = new HashMap<>();
                    subscriber.setPreferences(preferences);
                }
                
                Integer suspensionCount = (Integer) preferences.get("suspensionCount");
                if (suspensionCount == null) {
                    suspensionCount = 0;
                }
                
                // Increment suspension count
                suspensionCount++;
                preferences.put("suspensionCount", suspensionCount);
                
                // Set suspension reason (could be extracted from request context)
                preferences.put("suspensionReason", "delivery_issues");
                preferences.put("lastSuspensionDate", java.time.LocalDateTime.now().toString());
                
                // Check if suspension count exceeds limit
                if (suspensionCount > MAX_SUSPENSION_COUNT) {
                    logger.warn("Subscriber {} exceeded max suspension count ({}), will be unsubscribed", 
                               subscriber.getEmail(), MAX_SUSPENSION_COUNT);
                    
                    // Set isActive to false (effectively unsubscribing)
                    subscriber.setIsActive(false);
                    preferences.put("autoUnsubscribeReason", "exceeded_suspension_limit");
                    
                    // Apply unsubscribe transition asynchronously
                    UUID entityId = UUID.fromString(request.getEntityId());
                    entityService.applyTransition(entityId, "unsubscribe")
                        .thenAccept(transitions -> 
                            logger.info("Auto-unsubscribed subscriber {} due to suspension limit", subscriber.getEmail()))
                        .exceptionally(ex -> {
                            logger.error("Failed to auto-unsubscribe subscriber {}: {}", 
                                        subscriber.getEmail(), ex.getMessage());
                            return null;
                        });
                }
                
                logger.info("Subscriber suspended: {} (count: {}, reason: {})", 
                           subscriber.getEmail(), suspensionCount, "delivery_issues");
                
                return subscriber;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "SubscriberSuspensionProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates suspension data.
     * 
     * @param subscriber The subscriber to validate
     * @return true if valid, false otherwise
     */
    private boolean validateSuspensionData(Subscriber subscriber) {
        // Subscriber must exist
        if (subscriber == null) {
            logger.warn("Suspension failed: Subscriber is null");
            return false;
        }
        
        // Email must be set
        if (subscriber.getEmail() == null || subscriber.getEmail().trim().isEmpty()) {
            logger.warn("Suspension failed: Email is required");
            return false;
        }
        
        // Subscriber should currently be active
        if (subscriber.getIsActive() == null || !subscriber.getIsActive()) {
            logger.warn("Suspension failed: Subscriber is not active: {}", subscriber.getEmail());
            return false;
        }
        
        logger.debug("Suspension data validation passed for email: {}", subscriber.getEmail());
        return true;
    }
}
