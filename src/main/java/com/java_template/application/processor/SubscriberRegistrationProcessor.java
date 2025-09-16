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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Processor for subscriber registration workflow transition.
 * Handles the subscribe transition (none → pending_verification).
 * 
 * Business Logic:
 * - Validates email format
 * - Generates unique unsubscribe token
 * - Sets subscription date and default preferences
 * - Sets isActive to true
 */
@Component
public class SubscriberRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberRegistrationProcessor.class);
    private final ProcessorSerializer serializer;

    public SubscriberRegistrationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("SubscriberRegistrationProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing subscriber registration for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::validateSubscriberData, "Invalid subscriber data for registration")
            .map(ctx -> {
                Subscriber subscriber = ctx.entity();
                
                // Generate unique unsubscribe token
                subscriber.setUnsubscribeToken(UUID.randomUUID().toString());
                
                // Set subscription date to current timestamp
                subscriber.setSubscriptionDate(LocalDateTime.now());
                
                // Set isActive to true
                subscriber.setIsActive(true);
                
                // Set default preferences if not provided
                if (subscriber.getPreferences() == null) {
                    Map<String, Object> defaultPreferences = new HashMap<>();
                    defaultPreferences.put("frequency", "weekly");
                    defaultPreferences.put("format", "html");
                    subscriber.setPreferences(defaultPreferences);
                }
                
                logger.info("Subscriber registration processed for email: {}", subscriber.getEmail());
                return subscriber;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "SubscriberRegistrationProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates subscriber data for registration.
     * 
     * @param subscriber The subscriber to validate
     * @return true if valid, false otherwise
     */
    private boolean validateSubscriberData(Subscriber subscriber) {
        // Email is required
        if (subscriber.getEmail() == null || subscriber.getEmail().trim().isEmpty()) {
            logger.warn("Registration failed: Email is required");
            return false;
        }
        
        // Basic email format validation
        String email = subscriber.getEmail().trim();
        if (!email.contains("@") || !email.contains(".") || email.length() < 5) {
            logger.warn("Registration failed: Invalid email format: {}", email);
            return false;
        }
        
        // Email should not already have unsubscribe token (new registration)
        if (subscriber.getUnsubscribeToken() != null) {
            logger.warn("Registration failed: Subscriber already has unsubscribe token");
            return false;
        }
        
        // Subscription date should not be set (new registration)
        if (subscriber.getSubscriptionDate() != null) {
            logger.warn("Registration failed: Subscription date already set");
            return false;
        }
        
        logger.debug("Subscriber data validation passed for email: {}", email);
        return true;
    }
}
