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

import java.util.regex.Pattern;

/**
 * UserEmailValidationCriterion - Validate user email format and uniqueness
 * 
 * Transition: activate_user (none → active)
 * Purpose: Validate user email format and uniqueness
 */
@Component
public class UserEmailValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    // Simple email regex pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public UserEmailValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking User email validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User user = context.entityWithMetadata().entity();

        // Check if user is null (structural validation)
        if (user == null) {
            logger.warn("User is null");
            return EvaluationOutcome.fail("User is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!user.isValid()) {
            logger.warn("User is not valid");
            return EvaluationOutcome.fail("User is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check email is not empty or null
        if (user.getEmail() == null) {
            logger.warn("User email is null");
            return EvaluationOutcome.fail("User email must not be null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        String email = user.getEmail().trim();
        if (email.isEmpty()) {
            logger.warn("User email is empty");
            return EvaluationOutcome.fail("User email must not be empty", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate email format using regex pattern
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            logger.warn("User email format is invalid: {}", email);
            return EvaluationOutcome.fail("User email format is invalid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify email contains '@' and valid domain (additional check)
        if (!email.contains("@")) {
            logger.warn("User email missing @ symbol: {}", email);
            return EvaluationOutcome.fail("User email must contain @ symbol", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        String[] parts = email.split("@");
        if (parts.length != 2 || parts[1].isEmpty() || !parts[1].contains(".")) {
            logger.warn("User email domain is invalid: {}", email);
            return EvaluationOutcome.fail("User email must have valid domain", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check email length is reasonable (< 255 characters)
        if (email.length() >= 255) {
            logger.warn("User email is too long: {} characters", email.length());
            return EvaluationOutcome.fail("User email must be less than 255 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Note: Email uniqueness check would require external service call, which is not recommended in criteria
        // In a real system, this would be handled at the database level or in a processor

        logger.debug("User email validation passed for user: {}", user.getUserId());
        return EvaluationOutcome.success();
    }
}
