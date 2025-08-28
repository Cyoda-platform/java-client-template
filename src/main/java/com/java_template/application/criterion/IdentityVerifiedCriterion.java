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
public class IdentityVerifiedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IdentityVerifiedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name (case-sensitive)
        if (modelSpec == null || modelSpec.operationName() == null) {
            return false;
        }
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();
         if (user == null) {
             logger.debug("IdentityVerifiedCriterion: user is null");
             return EvaluationOutcome.fail("User entity missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required fields
         if (user.getEmail() == null || user.getEmail().isBlank()) {
             return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (user.getFullName() == null || user.getFullName().isBlank()) {
             return EvaluationOutcome.fail("Full name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (user.getRegisteredAt() == null || user.getRegisteredAt().isBlank()) {
             return EvaluationOutcome.fail("Registration timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // At least one contact (phone or address) should be present
         boolean hasPhone = user.getPhone() != null && !user.getPhone().isBlank();
         boolean hasAddress = user.getAddress() != null && !user.getAddress().isBlank();
         if (!hasPhone && !hasAddress) {
             return EvaluationOutcome.fail("At least one contact method (phone or address) is required",
                 StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic email format check
         String email = user.getEmail().trim();
         int atPos = email.indexOf('@');
         if (atPos <= 0 || atPos == email.length() - 1 || !email.substring(atPos + 1).contains(".")) {
             return EvaluationOutcome.fail("Email format is invalid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Basic phone sanity check if provided: require at least 7 digits
         if (hasPhone) {
             String digitsOnly = user.getPhone().replaceAll("\\D", "");
             if (digitsOnly.length() < 7) {
                 return EvaluationOutcome.fail("Phone number appears invalid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // If user status explicitly indicates suspension, fail business rule
         if (user.getStatus() != null && user.getStatus().equalsIgnoreCase("Suspended")) {
             return EvaluationOutcome.fail("User is suspended", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}