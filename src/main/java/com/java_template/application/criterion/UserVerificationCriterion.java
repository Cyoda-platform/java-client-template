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
 * UserVerificationCriterion - Validates that a user can be activated
 * 
 * Transition: activate_user
 * Purpose: Validates user verification and activation eligibility
 */
@Component
public class UserVerificationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserVerificationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking User verification criteria for request: {}", request.getId());
        
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
     * Main validation logic for user verification
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User user = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (user == null) {
            logger.warn("User entity is null");
            return EvaluationOutcome.fail("User entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify user is in registered state
        if (!"registered".equals(currentState)) {
            logger.warn("User {} is not in registered state, current state: {}", user.getUsername(), currentState);
            return EvaluationOutcome.fail("User is not in registered state for activation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check user validity
        if (!user.isValid()) {
            logger.warn("User {} is not valid", user.getUsername());
            return EvaluationOutcome.fail("User data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate email address format
        if (user.getEmail() == null || user.getEmail().trim().isEmpty() || !user.getEmail().contains("@")) {
            logger.warn("User {} has invalid email address", user.getUsername());
            return EvaluationOutcome.fail("Valid email address is required for activation", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check username uniqueness (basic validation)
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            logger.warn("User has empty username");
            return EvaluationOutcome.fail("Username is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate required personal information
        if (user.getFirstName() == null || user.getFirstName().trim().isEmpty()) {
            logger.warn("User {} has no first name", user.getUsername());
            return EvaluationOutcome.fail("First name is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (user.getLastName() == null || user.getLastName().trim().isEmpty()) {
            logger.warn("User {} has no last name", user.getUsername());
            return EvaluationOutcome.fail("Last name is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check password is encrypted/set
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            logger.warn("User {} has no password set", user.getUsername());
            return EvaluationOutcome.fail("Password is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check registration date is set
        if (user.getRegistrationDate() == null) {
            logger.warn("User {} has no registration date", user.getUsername());
            return EvaluationOutcome.fail("Registration date is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("User {} is verified and can be activated", user.getUsername());
        return EvaluationOutcome.success();
    }
}
