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
        // This is a predefined chain. Just write the business logic in validateEntity method.
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
         Owner entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Owner entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields: id, name, email, role
         if (isBlank(entity.getId())) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (isBlank(entity.getName())) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (isBlank(entity.getEmail())) {
             return EvaluationOutcome.fail("email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (isBlank(entity.getRole())) {
             return EvaluationOutcome.fail("role is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getVerified() == null) {
             return EvaluationOutcome.fail("verified flag must be present", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Email format basic validation
         if (!isValidEmail(entity.getEmail())) {
             return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Phone: if present, perform basic sanity checks (digits/+, length)
         if (entity.getPhone() != null && !entity.getPhone().isBlank()) {
             String phone = entity.getPhone().trim();
             String normalized = phone.replaceAll("[\\s\\-()]", "");
             if (normalized.length() < 6 || !normalized.matches("^[+0-9].*")) {
                 return EvaluationOutcome.fail("Phone number appears invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Favorites list quality check
         List<String> favorites = entity.getFavorites();
         if (favorites != null) {
             for (String fav : favorites) {
                 if (isBlank(fav)) {
                     return EvaluationOutcome.fail("favorites contains invalid pet id", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Business rule: role must be one of allowed values
         Set<String> allowedRoles = Set.of("user", "admin", "staff");
         if (!allowedRoles.contains(entity.getRole().toLowerCase())) {
             return EvaluationOutcome.fail("role must be one of [user, admin, staff]", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Business rule: if not verified -> action required (fail with BUSINESS_RULE_FAILURE)
         if (!Boolean.TRUE.equals(entity.getVerified())) {
             return EvaluationOutcome.fail("Owner contact not verified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean isValidEmail(String email) {
        if (isBlank(email)) return false;
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 0 || at == e.length() - 1) return false;
        String domain = e.substring(at + 1);
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".");
    }
}