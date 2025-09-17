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
 * UserReactivationEligibilityCriterion - Determines if an inactive user is eligible for reactivation
 * 
 * Transition: reactivate_inactive
 * Purpose: Validates inactive user reactivation eligibility
 */
@Component
public class UserReactivationEligibilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserReactivationEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking User reactivation eligibility criteria for request: {}", request.getId());
        
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
     * Main validation logic for inactive user reactivation eligibility
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User user = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (user == null) {
            logger.warn("User entity is null");
            return EvaluationOutcome.fail("User entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify user is in inactive state
        if (!"inactive".equals(currentState)) {
            logger.warn("User {} is not in inactive state, current state: {}", user.getUsername(), currentState);
            return EvaluationOutcome.fail("User is not in inactive state for reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check user validity
        if (!user.isValid()) {
            logger.warn("User {} is not valid", user.getUsername());
            return EvaluationOutcome.fail("User data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if account was not terminated for serious violations
        // In real system, this would check termination reasons
        if (user.getIsActive() != null && user.getIsActive()) {
            logger.warn("User {} shows as active but in inactive state - data inconsistency", user.getUsername());
            return EvaluationOutcome.fail("User data inconsistency detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate email address is still valid and accessible
        if (user.getEmail() == null || user.getEmail().trim().isEmpty() || !user.getEmail().contains("@")) {
            logger.warn("User {} has invalid email for reactivation", user.getUsername());
            return EvaluationOutcome.fail("Valid email is required for reactivation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate account information can be restored
        if (user.getFirstName() == null || user.getFirstName().trim().isEmpty()) {
            logger.warn("User {} has no first name for reactivation", user.getUsername());
            return EvaluationOutcome.fail("First name is required for reactivation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (user.getLastName() == null || user.getLastName().trim().isEmpty()) {
            logger.warn("User {} has no last name for reactivation", user.getUsername());
            return EvaluationOutcome.fail("Last name is required for reactivation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            logger.warn("User {} has no password for reactivation", user.getUsername());
            return EvaluationOutcome.fail("Password is required for reactivation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if user has been inactive for a reasonable period (prevent immediate reactivation)
        // This is a business rule to prevent abuse
        if (user.getRegistrationDate() != null) {
            long daysSinceRegistration = java.time.Duration.between(user.getRegistrationDate(), java.time.LocalDateTime.now()).toDays();
            if (daysSinceRegistration < 1) {
                logger.warn("User {} was registered too recently for reactivation ({} days)", user.getUsername(), daysSinceRegistration);
                return EvaluationOutcome.fail("User must be registered for at least 1 day before reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        logger.debug("Inactive user {} is eligible for reactivation", user.getUsername());
        return EvaluationOutcome.success();
    }
}
