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
public class DoubleOptInRequired implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DoubleOptInRequired(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Consent.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return "DoubleOptInRequired".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Consent> context) {
         Consent consent = context.entity();

         // Basic presence validation using only available getters
         if (consent == null) {
             return EvaluationOutcome.fail("Consent entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (consent.getConsent_id() == null || consent.getConsent_id().isBlank()) {
             return EvaluationOutcome.fail("consent_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (consent.getUser_id() == null || consent.getUser_id().isBlank()) {
             return EvaluationOutcome.fail("user_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (consent.getRequested_at() == null || consent.getRequested_at().isBlank()) {
             return EvaluationOutcome.fail("requested_at is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (consent.getStatus() == null || consent.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (consent.getType() == null || consent.getType().isBlank()) {
             return EvaluationOutcome.fail("type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String type = consent.getType().trim();
         String status = consent.getStatus().trim();
         String grantedAt = consent.getGranted_at();

         // Business rule: double opt-in is required for marketing consents.
         if ("marketing".equalsIgnoreCase(type)) {
             // If already granted -> ok
             if (grantedAt != null && !grantedAt.isBlank()) {
                 return EvaluationOutcome.success();
             }
             // Acceptable states where double-opt-in is either required or already initiated
             if ("requested".equalsIgnoreCase(status) || "pending_verification".equalsIgnoreCase(status)) {
                 return EvaluationOutcome.success();
             }
             // Any other state is unexpected for a marketing consent requiring double opt-in
             return EvaluationOutcome.fail(
                 "Marketing consent must be in 'requested' or 'pending_verification' or have granted_at when requiring double opt-in",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }

         // For non-marketing types double opt-in is not required by policy -> criterion should pass.
         return EvaluationOutcome.success();
    }
}