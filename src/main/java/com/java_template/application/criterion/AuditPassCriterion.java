package com.java_template.application.criterion;

import com.java_template.application.entity.pickledger.version_1.PickLedger;
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
public class AuditPassCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AuditPassCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PickLedger.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PickLedger> context) {
         PickLedger entity = context.entity();

         // Audit must be present and in an acceptable final state.
         String auditStatus = entity.getAuditStatus();

         if (auditStatus == null || auditStatus.isBlank()) {
             // Missing audit status indicates data quality issue (audit not recorded)
             return EvaluationOutcome.fail("Audit status missing for pick ledger", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Normalize for comparison
         String norm = auditStatus.trim().toUpperCase();

         // Explicit failure if audit failed
         if ("AUDIT_FAILED".equals(norm)) {
             return EvaluationOutcome.fail("Pick ledger failed audit", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If audit still pending, it's not acceptable for passing the audit criterion
         if ("AUDIT_PENDING".equals(norm)) {
             return EvaluationOutcome.fail("Audit still pending for pick ledger", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Accept only explicit AUDIT_PASSED as success (other unknown statuses treated as data issues)
         if ("AUDIT_PASSED".equals(norm)) {
             return EvaluationOutcome.success();
         }

         // Unknown status -> data quality problem
         return EvaluationOutcome.fail("Unknown audit status: " + auditStatus, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}