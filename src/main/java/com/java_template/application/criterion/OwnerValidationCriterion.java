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
import java.util.Set;

@Component
public class OwnerValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OwnerValidationCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
         Owner owner = context.entity();

         if (owner == null) {
             return EvaluationOutcome.fail("Owner entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields
         if (owner.getId() == null || owner.getId().isBlank()) {
             return EvaluationOutcome.fail("Owner.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (owner.getName() == null || owner.getName().isBlank()) {
             return EvaluationOutcome.fail("Owner.name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (owner.getEmail() == null || owner.getEmail().isBlank()) {
             return EvaluationOutcome.fail("Owner.email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (owner.getRole() == null || owner.getRole().isBlank()) {
             return EvaluationOutcome.fail("Owner.role is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (owner.getVerified() == null) {
             return EvaluationOutcome.fail("Owner.verified must be present", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Email format basic check
         String email = owner.getEmail();
         if (!email.matches("^.+@.+\\..+$")) {
             return EvaluationOutcome.fail("Owner.email has invalid format", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Role must be one of allowed values
         Set<String> allowedRoles = Set.of("user", "admin", "staff");
         String roleLower = owner.getRole() == null ? "" : owner.getRole().trim().toLowerCase();
         if (!allowedRoles.contains(roleLower)) {
             return EvaluationOutcome.fail("Owner.role is invalid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Phone (optional) basic quality check if present
         String phone = owner.getPhone();
         if (phone != null && !phone.isBlank()) {
             // Accept digits, spaces, parentheses, plus and dashes; require at least 5 digits overall
             if (!phone.matches("^[+\\d][\\d \\-()]{3,}$")) {
                 return EvaluationOutcome.fail("Owner.phone has invalid format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Favorites quality check
         List<String> favorites = owner.getFavorites();
         if (favorites != null) {
             for (String fav : favorites) {
                 if (fav == null || fav.isBlank()) {
                     return EvaluationOutcome.fail("Owner.favorites contains invalid entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Business rule: contact must be verified to progress to VERIFIED state
         if (!Boolean.TRUE.equals(owner.getVerified())) {
             return EvaluationOutcome.fail("Owner contact not verified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}