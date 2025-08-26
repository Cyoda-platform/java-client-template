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
         User user = context.entity();
         if (user == null) {
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required identifiers and basic fields
         if (user.getUserId() == null || user.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (user.getName() == null || user.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Email presence and basic format check
         if (user.getEmail() == null || user.getEmail().isBlank()) {
             return EvaluationOutcome.fail("email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         Pattern emailPattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
         if (!emailPattern.matcher(user.getEmail()).matches()) {
             return EvaluationOutcome.fail("email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Role: if provided must be one of allowed values. If missing, AssignRoleProcessor will handle defaulting.
         if (user.getRole() != null) {
             String role = user.getRole();
             if (!(role.equals("Admin") || role.equals("Customer"))) {
                 return EvaluationOutcome.fail("role must be 'Admin' or 'Customer' if provided", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Status: if provided must be valid
         if (user.getStatus() != null) {
             String status = user.getStatus();
             if (!(status.equalsIgnoreCase("active") || status.equalsIgnoreCase("inactive"))) {
                 return EvaluationOutcome.fail("status must be 'active' or 'inactive' if provided", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // createdAt, if present, should not be blank (format validity is outside this criterion's scope)
         if (user.getCreatedAt() != null && user.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}