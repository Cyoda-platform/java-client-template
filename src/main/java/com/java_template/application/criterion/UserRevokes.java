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
public class UserRevokes implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserRevokes(SerializerFactory serializerFactory) {
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
        if (modelSpec == null) return false;
        String opName = modelSpec.operationName();
        return opName != null && className.equals(opName);
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Consent> context) {
         Consent entity = context.entity();

         // Basic required identifiers check (use only getters present on the entity)
         if (entity.getConsent_id() == null || entity.getConsent_id().isBlank()) {
             return EvaluationOutcome.fail("consent_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUser_id() == null || entity.getUser_id().isBlank()) {
             return EvaluationOutcome.fail("user_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         String revokedAt = entity.getRevoked_at();

         // Ensure status is present
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules for user revocation:
         // - If status == "revoked", revoked_at must be provided.
         if ("revoked".equalsIgnoreCase(status)) {
             if (revokedAt == null || revokedAt.isBlank()) {
                 return EvaluationOutcome.fail("revoked_at must be set when status is 'revoked'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else {
             // - If revoked_at is present but status is not 'revoked', that's inconsistent.
             if (revokedAt != null && !revokedAt.isBlank()) {
                 return EvaluationOutcome.fail("revoked_at present but status is not 'revoked'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}