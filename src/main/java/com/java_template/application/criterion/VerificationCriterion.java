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

         // Basic required fields validation using only available getters
         if (user == null) {
             return EvaluationOutcome.fail("User entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (user.getId() == null || user.getId().isBlank()) {
             return EvaluationOutcome.fail("User id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (user.getEmail() == null || user.getEmail().isBlank()) {
             return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (user.getName() == null || user.getName().isBlank()) {
             return EvaluationOutcome.fail("User name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (user.getStatus() == null || user.getStatus().isBlank()) {
             return EvaluationOutcome.fail("User status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = user.getStatus().trim();

         // If the user is explicitly inactive, this fails business rule
         if (status.equalsIgnoreCase("INACTIVE") || status.equalsIgnoreCase("USER_INACTIVE")) {
             return EvaluationOutcome.fail("User is inactive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If the user is in a verification pending state, perform data quality checks to decide pass/fail
         if (status.equalsIgnoreCase("VERIFICATION_PENDING") || status.equalsIgnoreCase("VERIFICATIONPENDING") || status.equalsIgnoreCase("PENDING_VERIFICATION")) {
             String email = user.getEmail().trim();
             // Basic email format check: contains '@' and a dot in domain part
             int atIdx = email.indexOf('@');
             if (atIdx <= 0 || atIdx == email.length() - 1) {
                 return EvaluationOutcome.fail("Email format invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             String domain = email.substring(atIdx + 1);
             if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
                 return EvaluationOutcome.fail("Email domain appears invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // If phone provided, ensure it is not obviously invalid (basic digits check)
             if (user.getPhone() != null && !user.getPhone().isBlank()) {
                 String digits = user.getPhone().replaceAll("[^0-9]", "");
                 if (digits.length() < 7) {
                     return EvaluationOutcome.fail("Phone number appears invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
             // Basic checks passed — consider verification successful for automatic transition
             return EvaluationOutcome.success();
         }

         // If status is active (or equivalent), it's a success
         if (status.equalsIgnoreCase("ACTIVE") || status.equalsIgnoreCase("USER_ACTIVE")) {
             return EvaluationOutcome.success();
         }

         // For any other statuses, treat as data quality failure unless required fields missing
         logger.debug("User status '{}' is not explicitly handled by VerificationCriterion for user id {}", status, user.getId());
         return EvaluationOutcome.fail("User status not eligible for verification: " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}