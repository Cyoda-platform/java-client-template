package com.java_template.application.criterion;

import com.java_template.application.entity.owner.version_1.Owner;
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
public class ProfileCompletionCheck implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProfileCompletionCheck(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Owner.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
         Owner owner = context.entity();

         if (owner == null) {
             logger.debug("Owner entity is null");
             return EvaluationOutcome.fail("Owner entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Address is required for profile completion
         if (owner.getAddress() == null || owner.getAddress().isBlank()) {
             return EvaluationOutcome.fail("Address is required for profile completion", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Preferences required as part of a complete profile (business requirement)
         if (owner.getPreferences() == null || owner.getPreferences().isBlank()) {
             return EvaluationOutcome.fail("Preferences are required for profile completion", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // At least one contact method (email or phone) must be present
         boolean hasEmail = owner.getContactEmail() != null && !owner.getContactEmail().isBlank();
         boolean hasPhone = owner.getContactPhone() != null && !owner.getContactPhone().isBlank();
         if (!hasEmail && !hasPhone) {
             return EvaluationOutcome.fail("At least one contact method (email or phone) is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If email present, perform a simple format validation
         if (hasEmail && !isValidEmail(owner.getContactEmail())) {
             return EvaluationOutcome.fail("Invalid contactEmail format", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If phone present, perform a simple numeric length validation
         if (hasPhone && !isValidPhone(owner.getContactPhone())) {
             return EvaluationOutcome.fail("Invalid contactPhone format", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        String e = email.trim();
        if (e.isBlank()) return false;
        int at = e.indexOf('@');
        if (at <= 0) return false;
        int dot = e.indexOf('.', at);
        return dot > at + 1 && dot < e.length() - 1;
    }

    private boolean isValidPhone(String phone) {
        if (phone == null) return false;
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() >= 7 && digits.length() <= 15;
    }
}