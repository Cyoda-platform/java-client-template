package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * SubscriberValidationCriterion - Validates subscriber data and eligibility for activation/reactivation
 * 
 * Purpose: Validate subscriber data and eligibility for activation/reactivation
 * Input: Subscriber entity
 * Output: Boolean (true if valid, false otherwise)
 * 
 * Use Cases:
 * - PENDING → ACTIVE transition
 * - BOUNCED → ACTIVE transition
 * - UNSUBSCRIBED → ACTIVE transition
 */
@Component
public class SubscriberValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubscriberValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Subscriber validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, this::validateSubscriber)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the subscriber entity
     */
    private EvaluationOutcome validateSubscriber(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
        Subscriber subscriber = context.entityWithMetadata().entity();

        // Check if subscriber is null (structural validation)
        if (subscriber == null) {
            logger.warn("Subscriber is null");
            return EvaluationOutcome.fail("Subscriber entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!subscriber.isValid()) {
            logger.warn("Subscriber {} is not valid", subscriber.getEmail());
            return EvaluationOutcome.fail("Subscriber entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate email format
        if (!isValidEmailFormat(subscriber.getEmail())) {
            logger.warn("Subscriber {} has invalid email format", subscriber.getEmail());
            return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate unsubscribe token
        if (subscriber.getUnsubscribeToken() == null || subscriber.getUnsubscribeToken().trim().isEmpty()) {
            logger.warn("Subscriber {} has missing unsubscribe token", subscriber.getEmail());
            return EvaluationOutcome.fail("Missing unsubscribe token", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate subscription date
        if (subscriber.getSubscriptionDate() == null) {
            logger.warn("Subscriber {} has missing subscription date", subscriber.getEmail());
            return EvaluationOutcome.fail("Missing subscription date", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Subscription date cannot be in the future
        if (subscriber.getSubscriptionDate().isAfter(LocalDateTime.now())) {
            logger.warn("Subscriber {} has future subscription date", subscriber.getEmail());
            return EvaluationOutcome.fail("Subscription date cannot be in the future", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Subscriber {} validation passed", subscriber.getEmail());
        return EvaluationOutcome.success();
    }

    /**
     * Validates email format using basic rules
     */
    private boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        // Basic email validation
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }
}
