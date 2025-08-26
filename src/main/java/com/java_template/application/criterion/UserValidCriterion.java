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

    // Simple email validator - reasonable for basic validation without external dependencies
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public UserValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User entity = context.entity();
         if (entity == null) {
             logger.warn("User entity is null in {}", className);
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required: username
         if (entity.getUsername() == null || entity.getUsername().isBlank()) {
             return EvaluationOutcome.fail("username is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: email
         if (entity.getEmail() == null || entity.getEmail().isBlank()) {
             return EvaluationOutcome.fail("email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Email format validation
         if (!EMAIL_PATTERN.matcher(entity.getEmail().trim()).matches()) {
             return EvaluationOutcome.fail("email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: if entity already marked INVALID we should not pass it
         String status = entity.getValidationStatus();
         if (status != null && status.equalsIgnoreCase("INVALID")) {
             return EvaluationOutcome.fail("user marked as INVALID", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality warning: missing fullName or phone is a warning (not failure)
         if (entity.getFullName() == null || entity.getFullName().isBlank()) {
             // attach as a logger warning; serializer's warning attachment can't be invoked here because context doesn't expose it
             logger.warn("fullName is missing - will attempt transformation but data may be incomplete");
         }
         if (entity.getPhone() == null || entity.getPhone().isBlank()) {
             logger.warn("phone is missing - contact number absent");
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}