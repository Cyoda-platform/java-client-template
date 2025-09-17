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
 * UserReactivationCriterion - Validates that a suspended user can be reactivated
 * 
 * Transition: reactivate_user
 * Purpose: Validates user reactivation eligibility
 */
@Component
public class UserReactivationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserReactivationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking User reactivation criteria for request: {}", request.getId());
        
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
     * Main validation logic for user reactivation
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User user = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (user == null) {
            logger.warn("User entity is null");
            return EvaluationOutcome.fail("User entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify user is in suspended state
        if (!"suspended".equals(currentState)) {
            logger.warn("User {} is not in suspended state, current state: {}", user.getUsername(), currentState);
            return EvaluationOutcome.fail("User is not in suspended state for reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check user validity
        if (!user.isValid()) {
            logger.warn("User {} is not valid", user.getUsername());
            return EvaluationOutcome.fail("User data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check account information is still valid
        if (user.getEmail() == null || user.getEmail().trim().isEmpty() || !user.getEmail().contains("@")) {
            logger.warn("User {} has invalid email for reactivation", user.getUsername());
            return EvaluationOutcome.fail("Valid email is required for reactivation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate required personal information is still present
        if (user.getFirstName() == null || user.getFirstName().trim().isEmpty()) {
            logger.warn("User {} has no first name for reactivation", user.getUsername());
            return EvaluationOutcome.fail("First name is required for reactivation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (user.getLastName() == null || user.getLastName().trim().isEmpty()) {
            logger.warn("User {} has no last name for reactivation", user.getUsername());
            return EvaluationOutcome.fail("Last name is required for reactivation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check password is still set
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            logger.warn("User {} has no password for reactivation", user.getUsername());
            return EvaluationOutcome.fail("Password is required for reactivation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if suspension period has been served (minimum 1 day)
        // In real system, this would check against suspension details
        if (user.getRegistrationDate() != null) {
            long daysSinceRegistration = java.time.Duration.between(user.getRegistrationDate(), java.time.LocalDateTime.now()).toDays();
            if (daysSinceRegistration < 1) {
                logger.warn("User {} suspension period not served yet", user.getUsername());
                return EvaluationOutcome.fail("Minimum suspension period not served", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        logger.debug("User {} can be reactivated", user.getUsername());
        return EvaluationOutcome.success();
    }
}
