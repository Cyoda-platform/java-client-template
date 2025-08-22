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

@Component
public class VerificationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public VerificationCriterion(SerializerFactory serializerFactory) {
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
        // CRITICAL: must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();

         if (user == null) {
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields
         if (user.getId() == null || user.getId().isBlank()) {
             return EvaluationOutcome.fail("User id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (user.getName() == null || user.getName().isBlank()) {
             return EvaluationOutcome.fail("User name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (user.getStatus() == null || user.getStatus().isBlank()) {
             return EvaluationOutcome.fail("User status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Normalize status for checks
         String status = user.getStatus().trim();

         // Ensure at least one contact method exists (email or phone) for verification flows
         boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();
         boolean hasPhone = user.getPhone() != null && !user.getPhone().isBlank();

         // If user is expected to be verified (VERIFICATION_PENDING) there must be a contact method
         if ("VERIFICATION_PENDING".equalsIgnoreCase(status)) {
             if (!hasEmail && !hasPhone) {
                 return EvaluationOutcome.fail(
                     "User requires an email or phone to perform verification",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                 );
             }
             // Data quality checks: simple email and phone heuristics
             if (hasEmail) {
                 String email = user.getEmail().trim();
                 if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
                     return EvaluationOutcome.fail(
                         "User email format is invalid",
                         StandardEvalReasonCategories.DATA_QUALITY_FAILURE
                     );
                 }
             }
             if (hasPhone) {
                 String digits = user.getPhone().replaceAll("[^0-9]", "");
                 if (digits.length() < 7) {
                     return EvaluationOutcome.fail(
                         "User phone number appears too short",
                         StandardEvalReasonCategories.DATA_QUALITY_FAILURE
                     );
                 }
             }
             // If basic checks pass, evaluation success for verification criterion
             return EvaluationOutcome.success();
         }

         // If user is Active, verification criterion passes
         if ("Active".equalsIgnoreCase(status) || "USER_ACTIVE".equalsIgnoreCase(status) || "USER_ACTIVE".equals(status)) {
             return EvaluationOutcome.success();
         }

         // If user is Inactive, this is a business rule failure for verification
         if ("Inactive".equalsIgnoreCase(status) || "USER_INACTIVE".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail(
                 "User is inactive and cannot be verified",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }

         // Unknown status - treat as data quality / business rule concern
         logger.debug("User status '{}' is not explicitly handled by VerificationCriterion for user id {}", status, user.getId());
         return EvaluationOutcome.fail(
             String.format("Unhandled user status: '%s'", status),
             StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
         );
    }
}