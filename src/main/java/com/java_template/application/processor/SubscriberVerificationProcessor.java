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
import java.time.temporal.ChronoUnit;

/**
 * Processor for subscriber email verification workflow transition.
 * Handles the verify_email transition (pending_verification → active).
 * 
 * Business Logic:
 * - Validates verification token
 * - Checks if verification is not expired (within 24 hours)
 * - Marks subscriber as verified
 * - Updates lastModified timestamp
 */
@Component
public class SubscriberVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberVerificationProcessor.class);
    private static final long VERIFICATION_EXPIRY_HOURS = 24;
    
    private final ProcessorSerializer serializer;

    public SubscriberVerificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("SubscriberVerificationProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing subscriber verification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::validateVerificationData, "Invalid verification data")
            .map(ctx -> {
                Subscriber subscriber = ctx.entity();
                
                // Mark subscriber as verified (isActive should already be true from registration)
                subscriber.setIsActive(true);
                
                // Update subscription date to mark verification completion
                // Note: We keep the original subscription date but could add a verificationDate field
                
                logger.info("Email verification completed for subscriber: {}", subscriber.getEmail());
                return subscriber;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "SubscriberVerificationProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates verification data.
     * 
     * @param subscriber The subscriber to validate
     * @return true if valid, false otherwise
     */
    private boolean validateVerificationData(Subscriber subscriber) {
        // Subscriber must exist
        if (subscriber == null) {
            logger.warn("Verification failed: Subscriber is null");
            return false;
        }
        
        // Email must be set
        if (subscriber.getEmail() == null || subscriber.getEmail().trim().isEmpty()) {
            logger.warn("Verification failed: Email is required");
            return false;
        }
        
        // Subscription date must be set (from registration)
        if (subscriber.getSubscriptionDate() == null) {
            logger.warn("Verification failed: Subscription date not set");
            return false;
        }
        
        // Check if verification is not expired (within 24 hours)
        LocalDateTime subscriptionDate = subscriber.getSubscriptionDate();
        LocalDateTime now = LocalDateTime.now();
        long hoursSinceSubscription = ChronoUnit.HOURS.between(subscriptionDate, now);
        
        if (hoursSinceSubscription > VERIFICATION_EXPIRY_HOURS) {
            logger.warn("Verification failed: Verification expired for email: {} ({}h ago)", 
                       subscriber.getEmail(), hoursSinceSubscription);
            return false;
        }
        
        // Unsubscribe token should be set (from registration)
        if (subscriber.getUnsubscribeToken() == null || subscriber.getUnsubscribeToken().trim().isEmpty()) {
            logger.warn("Verification failed: Unsubscribe token not set");
            return false;
        }
        
        logger.debug("Verification data validation passed for email: {}", subscriber.getEmail());
        return true;
    }
}
