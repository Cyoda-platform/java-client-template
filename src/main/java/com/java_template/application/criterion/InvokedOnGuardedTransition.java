package com.java_template.application.criterion;

import com.java_template.application.entity.audit.version_1.Audit;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
public class InvokedOnGuardedTransition implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InvokedOnGuardedTransition(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Audit.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Audit> context) {
         Audit audit = context.entity();

         if (audit == null) {
             logger.warn("Audit entity is null in InvokedOnGuardedTransition criterion");
             return EvaluationOutcome.fail("Audit entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required core fields
         if (audit.getAuditId() == null || audit.getAuditId().isBlank()) {
             return EvaluationOutcome.fail("auditId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (audit.getAction() == null || audit.getAction().isBlank()) {
             return EvaluationOutcome.fail("action is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (audit.getActorId() == null || audit.getActorId().isBlank()) {
             return EvaluationOutcome.fail("actorId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (audit.getEntityRef() == null || audit.getEntityRef().isBlank()) {
             return EvaluationOutcome.fail("entityRef is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (audit.getTimestamp() == null || audit.getTimestamp().isBlank()) {
             return EvaluationOutcome.fail("timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // entityRef should follow the expected "id:Type" pattern (e.g., "post-123:Post")
         String entityRef = audit.getEntityRef();
         int sep = entityRef.indexOf(':');
         if (sep <= 0 || sep == entityRef.length() - 1) {
             return EvaluationOutcome.fail("entityRef must be in format 'id:Type'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         String refId = entityRef.substring(0, sep).trim();
         String refType = entityRef.substring(sep + 1).trim();
         if (refId.isBlank() || refType.isBlank()) {
             return EvaluationOutcome.fail("entityRef must contain non-blank id and type separated by ':'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // timestamp must be valid ISO-8601 instant
         try {
             Instant.parse(audit.getTimestamp());
         } catch (DateTimeParseException e) {
             return EvaluationOutcome.fail("timestamp must be a valid ISO-8601 instant", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: certain guarded actions must include transfer metadata
         String action = audit.getAction().trim();
         if ("gdpr_transfer".equalsIgnoreCase(action)) {
             Map<String, Object> metadata = audit.getMetadata();
             if (metadata == null) {
                 return EvaluationOutcome.fail("metadata with transfer details is required for gdpr_transfer", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             Object from = metadata.get("from");
             Object to = metadata.get("to");
             if (from == null || !(from instanceof String) || ((String) from).isBlank()) {
                 return EvaluationOutcome.fail("metadata.from is required for gdpr_transfer", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (to == null || !(to instanceof String) || ((String) to).isBlank()) {
                 return EvaluationOutcome.fail("metadata.to is required for gdpr_transfer", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // If evidenceRef provided, ensure it's not blank
         if (audit.getEvidenceRef() != null && audit.getEvidenceRef().isBlank()) {
             return EvaluationOutcome.fail("evidenceRef, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}