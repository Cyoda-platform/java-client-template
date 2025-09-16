package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Criterion to check if email verification has expired.
 * Handles the expire_verification transition (pending_verification → unsubscribed).
 * 
 * Validation Logic:
 * - Checks if verification age > 24 hours from subscription date
 * - Returns success if verification has expired (criteria met for expiration)
 * - Returns fail if verification is still valid
 */
@Component
public class SubscriberVerificationExpirationCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberVerificationExpirationCriterion.class);
    private static final long VERIFICATION_EXPIRY_HOURS = 24;
    
    private final CriterionSerializer serializer;

    public SubscriberVerificationExpirationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("SubscriberVerificationExpirationCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking subscriber verification expiration for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, ctx -> this.evaluateVerificationExpiration(ctx.entity()))
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "SubscriberVerificationExpirationCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether email verification has expired.
     * 
     * @param subscriber The subscriber to evaluate
     * @return EvaluationOutcome indicating whether verification has expired
     */
    private EvaluationOutcome evaluateVerificationExpiration(Subscriber subscriber) {
        if (subscriber == null) {
            return EvaluationOutcome.fail("Subscriber is null");
        }

        // Check if subscription date is set
        if (subscriber.getSubscriptionDate() == null) {
            return EvaluationOutcome.fail("Subscription date is not set");
        }

        // Check if subscriber email is set
        if (subscriber.getEmail() == null || subscriber.getEmail().trim().isEmpty()) {
            return EvaluationOutcome.fail("Subscriber email is not set");
        }

        // Calculate verification age
        LocalDateTime subscriptionDate = subscriber.getSubscriptionDate();
        LocalDateTime currentTime = LocalDateTime.now();
        long hoursSinceSubscription = ChronoUnit.HOURS.between(subscriptionDate, currentTime);

        // Check if verification has expired
        if (hoursSinceSubscription > VERIFICATION_EXPIRY_HOURS) {
            logger.info("Email verification expired for subscriber {} ({}h ago)", 
                       subscriber.getEmail(), hoursSinceSubscription);
            return EvaluationOutcome.success(); // Criteria met for expiration
        }

        // Verification is still valid
        logger.debug("Email verification still valid for subscriber {} ({}h ago)", 
                    subscriber.getEmail(), hoursSinceSubscription);
        return EvaluationOutcome.fail("Email verification still valid");
    }
}
