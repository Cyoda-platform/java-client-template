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
public class VerificationReceived implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public VerificationReceived(SerializerFactory serializerFactory) {
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

         // Basic required fields validation
         if (entity.getConsent_id() == null || entity.getConsent_id().isBlank()) {
             return EvaluationOutcome.fail("consent_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUser_id() == null || entity.getUser_id().isBlank()) {
             return EvaluationOutcome.fail("user_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequested_at() == null || entity.getRequested_at().isBlank()) {
             return EvaluationOutcome.fail("requested_at is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();

         // Business logic: VerificationReceived means the consent has been activated via verification.
         // Expect status == "active" and evidence_ref + granted_at populated.
         if ("active".equalsIgnoreCase(status)) {
             if (entity.getEvidence_ref() == null || entity.getEvidence_ref().isBlank()) {
                 return EvaluationOutcome.fail("evidence_ref must be present when verification is received", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (entity.getGranted_at() == null || entity.getGranted_at().isBlank()) {
                 return EvaluationOutcome.fail("granted_at must be present when verification is received", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // If still pending verification, this criterion should fail validation.
         if ("pending_verification".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("consent has not been verified yet", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Other statuses (requested, revoked, etc.) are not valid for verification_received processing
         return EvaluationOutcome.fail("consent is in an unexpected state for verification_received: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}