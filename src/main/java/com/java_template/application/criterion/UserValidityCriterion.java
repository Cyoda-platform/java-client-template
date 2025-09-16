package com.java_template.application.criterion;

import com.java_template.application.entity.user.version_1.User;
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

import java.util.regex.Pattern;

/**
 * Criterion for validating that a user can be registered.
 * Used in the register_user transition from initial_state to registered.
 */
@Component
public class UserValidityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(UserValidityCriterion.class);
    private final CriterionSerializer serializer;
    
    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    // Phone validation pattern (basic international format)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[1-9]\\d{1,14}$"
    );

    public UserValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking user validity for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(User.class, this::validateUser)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome validateUser(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User user = context.entity();
        return validateUserExists(user)
            .and(validateUsername(user))
            .and(validateEmail(user))
            .and(validatePassword(user))
            .and(validatePhone(user))
            .and(validateNames(user));
    }

    private EvaluationOutcome validateUserExists(User user) {
        if (user == null) {
            return EvaluationOutcome.Fail.businessRuleFailure("User entity is null");
        }
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateUsername(User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return EvaluationOutcome.Fail.businessRuleFailure("Username is required");
        }
        
        if (user.getUsername().length() < 3 || user.getUsername().length() > 50) {
            return EvaluationOutcome.Fail.businessRuleFailure("Username must be 3-50 characters");
        }
        
        // Note: In a real implementation, we would check if username already exists
        // using entityService.getFirstItemByCondition()
        
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateEmail(User user) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            return EvaluationOutcome.Fail.businessRuleFailure("Email is required");
        }
        
        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            return EvaluationOutcome.Fail.businessRuleFailure("Invalid email format");
        }
        
        // Note: In a real implementation, we would check if email already exists
        // using entityService.getFirstItemByCondition()
        
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePassword(User user) {
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return EvaluationOutcome.Fail.businessRuleFailure("Password is required");
        }
        
        if (user.getPassword().length() < 8) {
            return EvaluationOutcome.Fail.businessRuleFailure("Password must be at least 8 characters");
        }
        
        if (!containsUppercase(user.getPassword())) {
            return EvaluationOutcome.Fail.businessRuleFailure("Password must contain uppercase letter");
        }
        
        if (!containsLowercase(user.getPassword())) {
            return EvaluationOutcome.Fail.businessRuleFailure("Password must contain lowercase letter");
        }
        
        if (!containsDigit(user.getPassword())) {
            return EvaluationOutcome.Fail.businessRuleFailure("Password must contain digit");
        }
        
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePhone(User user) {
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
            if (!PHONE_PATTERN.matcher(user.getPhone().replaceAll("[\\s-()]", "")).matches()) {
                return EvaluationOutcome.Fail.businessRuleFailure("Invalid phone format");
            }
        }
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateNames(User user) {
        if (user.getFirstName() != null && user.getFirstName().length() > 50) {
            return EvaluationOutcome.Fail.businessRuleFailure("First name too long (max 50 characters)");
        }
        
        if (user.getLastName() != null && user.getLastName().length() > 50) {
            return EvaluationOutcome.Fail.businessRuleFailure("Last name too long (max 50 characters)");
        }
        
        return EvaluationOutcome.success();
    }

    private boolean containsUppercase(String password) {
        return password.chars().anyMatch(Character::isUpperCase);
    }

    private boolean containsLowercase(String password) {
        return password.chars().anyMatch(Character::isLowerCase);
    }

    private boolean containsDigit(String password) {
        return password.chars().anyMatch(Character::isDigit);
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "UserValidityCriterion".equals(opSpec.operationName()) &&
               "User".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
