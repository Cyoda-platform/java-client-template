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

/**
 * EmailBounceCriterion - Determines if email delivery failure should trigger bounce state
 * 
 * Purpose: Determine if email delivery failure should trigger bounce state
 * Input: Subscriber entity with bounce information
 * Output: Boolean (true if should bounce, false otherwise)
 * 
 * Use Cases:
 * - ACTIVE → BOUNCED transition
 */
@Component
public class EmailBounceCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailBounceCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Email bounce criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, this::validateEmailBounce)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for email bounce determination
     */
    private EvaluationOutcome validateEmailBounce(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
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

        // If subscriber is already inactive, should bounce
        if (subscriber.getIsActive() != null && !subscriber.getIsActive()) {
            logger.debug("Subscriber {} is already inactive, should bounce", subscriber.getEmail());
            return EvaluationOutcome.success();
        }

        // Simulate bounce type determination based on email patterns
        String bounceType = determineBounceType(subscriber.getEmail());

        // Hard bounce - immediate bounce
        if ("hard".equals(bounceType)) {
            logger.debug("Subscriber {} has hard bounce, should bounce", subscriber.getEmail());
            return EvaluationOutcome.success();
        }

        // Soft bounce - check bounce count (simulated)
        if ("soft".equals(bounceType)) {
            int bounceCount = simulateBounceCount(subscriber);
            if (bounceCount > 3) {
                logger.debug("Subscriber {} has soft bounce count {} > 3, should bounce",
                           subscriber.getEmail(), bounceCount);
                return EvaluationOutcome.success();
            } else {
                logger.debug("Subscriber {} has soft bounce count {} <= 3, should not bounce",
                           subscriber.getEmail(), bounceCount);
                return EvaluationOutcome.fail("Soft bounce count within threshold", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // No bounce condition met
        logger.debug("Subscriber {} does not meet bounce criteria", subscriber.getEmail());
        return EvaluationOutcome.fail("No bounce condition met", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    /**
     * Determines bounce type based on email characteristics
     * In a real implementation, this would be based on actual bounce information
     */
    private String determineBounceType(String email) {
        // Simulate bounce type determination
        if (email.contains("invalid") || email.contains("nonexistent")) {
            return "hard";
        } else if (email.contains("temp") || email.contains("full")) {
            return "soft";
        }
        return "none";
    }

    /**
     * Simulates bounce count for soft bounces
     * In a real implementation, this would be tracked in the subscriber entity or separate bounce tracking
     */
    private int simulateBounceCount(Subscriber subscriber) {
        // Simulate bounce count based on email hash
        return Math.abs(subscriber.getEmail().hashCode()) % 6; // Returns 0-5
    }
}
