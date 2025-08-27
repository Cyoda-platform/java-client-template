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

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Component
public class UserRevokesCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserRevokesCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Consent> context) {
         Consent consent = context.entity();

         // Basic expectation: this criterion represents a user-initiated revocation.
         // Therefore the consent.status must be 'revoked' and revokedAt must be present and a valid ISO timestamp.
         if (consent == null) {
             return EvaluationOutcome.fail("Consent entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = consent.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Consent.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (!"revoked".equalsIgnoreCase(status.trim())) {
             // The criterion is specifically for handling user revocations; if status is not revoked the guard should fail.
             return EvaluationOutcome.fail("Consent.status must be 'revoked' for user revocation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // When status is revoked, revokedAt must be present
         String revokedAt = consent.getRevokedAt();
         if (revokedAt == null || revokedAt.isBlank()) {
             return EvaluationOutcome.fail("revokedAt is required when status is 'revoked'", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If grantedAt exists, revokedAt must be after grantedAt (business rule)
         String grantedAt = consent.getGrantedAt();
         try {
             OffsetDateTime revokedTime = OffsetDateTime.parse(revokedAt);
             if (grantedAt != null && !grantedAt.isBlank()) {
                 OffsetDateTime grantedTime = OffsetDateTime.parse(grantedAt);
                 if (revokedTime.isBefore(grantedTime) || revokedTime.isEqual(grantedTime)) {
                     return EvaluationOutcome.fail("revokedAt must be after grantedAt", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             }
         } catch (DateTimeParseException ex) {
             logger.debug("Failed to parse date in Consent for user revokes criterion", ex);
             return EvaluationOutcome.fail("revokedAt (or grantedAt) is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Evidence may be optional; if present ensure it's not blank
         if (consent.getEvidenceRef() != null && consent.getEvidenceRef().isBlank()) {
             return EvaluationOutcome.fail("evidenceRef, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}