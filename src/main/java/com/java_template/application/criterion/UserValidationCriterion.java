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
 * UserValidationCriterion - Validates user data before activation
 * Checks if user meets all requirements for activation
 */
@Component
public class UserValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking User validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(User.class, this::validateUser)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the user entity
     */
    private EvaluationOutcome validateUser(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User user = context.entityWithMetadata().entity();

        // Check if user is null (structural validation)
        if (user == null) {
            logger.warn("User is null");
            return EvaluationOutcome.fail("User is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!user.isValid()) {
            logger.warn("User is not valid: {}", user.getEmail());
            return EvaluationOutcome.fail("User is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check email format
        if (!isValidEmailFormat(user.getEmail())) {
            logger.warn("Invalid email format: {}", user.getEmail());
            return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check role validity
        if (!isValidRole(user.getRole())) {
            logger.warn("Invalid role: {}", user.getRole());
            return EvaluationOutcome.fail("Invalid user role", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check organization is provided
        if (user.getOrganization() == null || user.getOrganization().trim().isEmpty()) {
            logger.warn("Organization is required for user: {}", user.getEmail());
            return EvaluationOutcome.fail("Organization is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check registration date is set
        if (user.getRegistrationDate() == null) {
            logger.warn("Registration date is missing for user: {}", user.getEmail());
            return EvaluationOutcome.fail("Registration date is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates email format
     */
    private boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        // Basic email validation
        return email.contains("@") && email.contains(".") && 
               email.indexOf("@") > 0 && 
               email.indexOf("@") < email.lastIndexOf(".");
    }

    /**
     * Validates if the role is one of the allowed values
     */
    private boolean isValidRole(String role) {
        return "EXTERNAL_SUBMITTER".equals(role) || 
               "REVIEWER".equals(role) || 
               "ADMIN".equals(role);
    }
}
