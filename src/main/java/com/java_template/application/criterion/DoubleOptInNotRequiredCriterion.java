package com.java_template.application.criterion;

import com.java_template.application.entity.consent.version_1.Consent;
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
public class DoubleOptInNotRequiredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DoubleOptInNotRequiredCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Consent.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Consent> context) {
         Consent entity = context.entity();

         // Basic validation: type is required to determine whether double opt-in applies
         if (entity.getType() == null || entity.getType().isBlank()) {
             return EvaluationOutcome.fail("Consent.type is required to evaluate double opt-in rules", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If consent already granted (evidence of verification), double opt-in is not required
         if (entity.getGrantedAt() != null && !entity.getGrantedAt().isBlank()) {
             return EvaluationOutcome.success();
         }

         String type = entity.getType().trim();
         String source = entity.getSource();

         // Define trusted sources that do not require double opt-in (e.g., admin/system/import)
         boolean trustedSource = false;
         if (source != null && !source.isBlank()) {
             String s = source.trim().toLowerCase();
             if (s.equals("admin") || s.equals("system") || s.equals("import")) {
                 trustedSource = true;
             }
         }

         // Business rule:
         // - Marketing consents require double opt-in unless the source is trusted or consent already granted.
         // - Non-marketing consents do not require double opt-in.
         if ("marketing".equalsIgnoreCase(type)) {
             if (trustedSource) {
                 return EvaluationOutcome.success();
             }
             // If marketing consent and not trusted source and not yet granted => double opt-in required -> fail this criterion
             return EvaluationOutcome.fail("Marketing consent from this source requires double opt-in verification", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All other consent types: double opt-in not required
         return EvaluationOutcome.success();
    }
}