package com.java_template.application.criterion;

import com.java_template.application.entity.user.version_1.User;
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
 * UserSuspensionCriterion - Determines if a user account should be suspended
 * 
 * Transition: suspend_user
 * Purpose: Validates user suspension eligibility and reasons
 */
@Component
public class UserSuspensionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserSuspensionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking User suspension criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for user suspension
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User user = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (user == null) {
            logger.warn("User entity is null");
            return EvaluationOutcome.fail("User entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify user is in active state
        if (!"active".equals(currentState)) {
            logger.warn("User {} is not in active state, current state: {}", user.getUsername(), currentState);
            return EvaluationOutcome.fail("User is not in active state for suspension", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check user validity
        if (!user.isValid()) {
            logger.warn("User {} is not valid", user.getUsername());
            return EvaluationOutcome.fail("User data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if user is currently active
        if (user.getIsActive() == null || !user.getIsActive()) {
            logger.warn("User {} is already inactive", user.getUsername());
            return EvaluationOutcome.fail("User is already inactive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Basic validation - in real system, would check for specific suspension reasons
        // such as policy violations, fraudulent activity, etc.
        
        // Check if user has valid account information
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            logger.warn("User {} has invalid email for suspension processing", user.getUsername());
            return EvaluationOutcome.fail("Valid email is required for suspension processing", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if user has been registered for a reasonable time (prevent immediate suspension)
        if (user.getRegistrationDate() != null) {
            long daysSinceRegistration = java.time.Duration.between(user.getRegistrationDate(), java.time.LocalDateTime.now()).toDays();
            if (daysSinceRegistration < 1) {
                logger.warn("User {} was registered too recently for suspension ({} days)", user.getUsername(), daysSinceRegistration);
                return EvaluationOutcome.fail("User must be registered for at least 1 day before suspension", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        logger.debug("User {} can be suspended", user.getUsername());
        return EvaluationOutcome.success();
    }
}
