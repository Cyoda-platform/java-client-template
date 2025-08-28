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

import java.util.List;
import java.util.regex.Pattern;

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

         // Required fields
         if (owner.getOwnerId() == null || owner.getOwnerId().isBlank()) {
             return EvaluationOutcome.fail("ownerId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (owner.getName() == null || owner.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (owner.getContactEmail() == null || owner.getContactEmail().isBlank()) {
             return EvaluationOutcome.fail("contactEmail is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Email format validation (simple RFC-light check)
         String email = owner.getContactEmail();
         Pattern emailPattern = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
         if (!emailPattern.matcher(email).matches()) {
             return EvaluationOutcome.fail("contactEmail is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Phone validation if provided (allow +, digits, spaces, hyphens; length 7-20)
         String phone = owner.getContactPhone();
         if (phone != null && !phone.isBlank()) {
             Pattern phonePattern = Pattern.compile("^[+]?[- 0-9]{7,20}$");
             if (!phonePattern.matcher(phone).matches()) {
                 return EvaluationOutcome.fail("contactPhone is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Role business rule: if present, must be one of expected values
         String role = owner.getRole();
         if (role != null && !role.isBlank()) {
             String normalized = role.trim().toLowerCase();
             if (!("user".equals(normalized) || "admin".equals(normalized) || "staff".equals(normalized))) {
                 return EvaluationOutcome.fail("role must be one of [user, admin, staff]", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Saved/adopted pet references quality check
         List<String> saved = owner.getSavedPets();
         if (saved != null) {
             for (String s : saved) {
                 if (s == null || s.isBlank()) {
                     return EvaluationOutcome.fail("savedPets must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }
         List<String> adopted = owner.getAdoptedPets();
         if (adopted != null) {
             for (String s : adopted) {
                 if (s == null || s.isBlank()) {
                     return EvaluationOutcome.fail("adoptedPets must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // If all checks pass, mark as successful evaluation
         return EvaluationOutcome.success();
    }
}