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

@Component
public class UserValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Simple email pattern: not exhaustive but adequate for validation purpose
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public UserValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User entity = context.entity();

         if (entity == null) {
             logger.debug("User entity is null in validation context");
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required fields: username and email (per entity isValid and business rules)
         String username = entity.getUsername();
         String email = entity.getEmail();

         if (username == null || username.isBlank()) {
             return EvaluationOutcome.fail("username is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (email == null || email.isBlank()) {
             return EvaluationOutcome.fail("email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic email format validation
         if (!EMAIL_PATTERN.matcher(email).matches()) {
             return EvaluationOutcome.fail("email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: usernames must not contain whitespace characters
         if (username.chars().anyMatch(Character::isWhitespace)) {
             return EvaluationOutcome.fail("username must not contain spaces", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality check: ensure the source fetched timestamp is present
         if (entity.getSourceFetchedAt() == null) {
             return EvaluationOutcome.fail("sourceFetchedAt timestamp is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}