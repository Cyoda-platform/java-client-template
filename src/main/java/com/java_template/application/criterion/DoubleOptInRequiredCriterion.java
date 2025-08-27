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
public class DoubleOptInRequiredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DoubleOptInRequiredCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Consent> context) {
         Consent consent = context.entity();
         if (consent == null) {
             return EvaluationOutcome.fail("Consent entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String type = consent.getType();
         String status = consent.getStatus();
         String requestedAt = consent.getRequestedAt();

         // Type is required to determine whether double opt-in applies
         if (type == null || type.isBlank()) {
             return EvaluationOutcome.fail("Consent type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Double opt-in rule only applies to marketing consents
         if (!"marketing".equalsIgnoreCase(type)) {
             return EvaluationOutcome.success();
         }

         // For marketing consents, requestedAt must be provided
         if (requestedAt == null || requestedAt.isBlank()) {
             return EvaluationOutcome.fail("requestedAt is required for marketing consent", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be present
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required for marketing consent", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String s = status.trim().toLowerCase();

         // Acceptable flows for marketing consent:
         // - requested: initial persisted state (processor may move to pending_verification)
         if ("requested".equals(s)) {
             return EvaluationOutcome.success();
         }

         // - pending_verification: waiting for user confirmation (expected)
         if ("pending_verification".equals(s)) {
             return EvaluationOutcome.success();
         }

         // - active: only allowed when verification evidence is present (double-opt-in completed)
         if ("active".equals(s)) {
             if (consent.getGrantedAt() == null || consent.getGrantedAt().isBlank()) {
                 return EvaluationOutcome.fail("marketing consent cannot be active without grantedAt (verification evidence)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (consent.getEvidenceRef() == null || consent.getEvidenceRef().isBlank()) {
                 return EvaluationOutcome.fail("marketing consent cannot be active without evidenceRef (verification evidence)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // - revoked: allowed, but prefer having revokedAt for audit/quality
         if ("revoked".equals(s)) {
             if (consent.getRevokedAt() == null || consent.getRevokedAt().isBlank()) {
                 return EvaluationOutcome.fail("revoked marketing consent should include revokedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // Any other status is considered a violation: marketing consent must follow double opt-in lifecycle
         return EvaluationOutcome.fail("marketing consent must require double opt-in (expected statuses: requested, pending_verification, active (with evidence), or revoked)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}