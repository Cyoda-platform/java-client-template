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
public class VerificationReceivedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public VerificationReceivedCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Consent> context) {
         Consent entity = context.entity();
         // Validation of required identity fields
         if (entity.getConsentId() == null || entity.getConsentId().isBlank()) {
             return EvaluationOutcome.fail("consentId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getType() == null || entity.getType().isBlank()) {
             return EvaluationOutcome.fail("type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business logic for verification_received:
         // The consent should reach status "active" and carry evidence/granted timestamp when verification is received.
         String status = entity.getStatus().trim().toLowerCase();
         if ("active".equals(status)) {
             if (entity.getEvidenceRef() == null || entity.getEvidenceRef().isBlank()) {
                 return EvaluationOutcome.fail("verification evidence is missing for active consent", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (entity.getGrantedAt() == null || entity.getGrantedAt().isBlank()) {
                 return EvaluationOutcome.fail("grantedAt timestamp is required for active consent", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // All checks passed for an activated consent
             return EvaluationOutcome.success();
         }

         // If still pending verification, the criterion should fail as verification hasn't been recorded yet.
         if ("pending_verification".equals(status) || "requested".equals(status)) {
             return EvaluationOutcome.fail("verification not recorded yet", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If consent is revoked, it cannot be verified
         if ("revoked".equals(status)) {
             return EvaluationOutcome.fail("consent has been revoked", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Any other status is unexpected for this criterion
         return EvaluationOutcome.fail("unexpected consent status for verification_received: " + entity.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}