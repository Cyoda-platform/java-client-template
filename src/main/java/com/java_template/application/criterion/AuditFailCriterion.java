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
public class AuditFailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AuditFailCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PickLedger> context) {
         PickLedger entity = context.entity();
         if (entity == null) {
             logger.warn("AuditFailCriterion invoked with null entity");
             return EvaluationOutcome.success();
         }

         String auditStatus = entity.getAuditStatus();
         Integer qtyRequested = entity.getQtyRequested();
         Integer qtyPicked = entity.getQtyPicked();
         String auditorId = entity.getAuditorId();

         // Primary business rule: if audit marked as failed -> criterion fails
         if ("AUDIT_FAILED".equals(auditStatus)) {
             String msg = String.format("PickLedger %s failed audit", entity.getId());
             logger.info(msg + " (orderId={}, shipmentId={}, productId={})",
                     entity.getOrderId(), entity.getShipmentId(), entity.getProductId());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality checks related to audit results
         if ("AUDIT_PASSED".equals(auditStatus)) {
             // auditor must be present when audit passed
             if (auditorId == null || auditorId.isBlank()) {
                 String msg = String.format("PickLedger %s audit passed but missing auditorId", entity.getId());
                 logger.warn(msg);
                 return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // qtyPicked should not be less than qtyRequested when audit passed (indicates mismatch)
             if (qtyRequested != null && qtyPicked != null && qtyPicked < qtyRequested) {
                 String msg = String.format("PickLedger %s audit passed but qtyPicked (%d) < qtyRequested (%d)",
                         entity.getId(), qtyPicked, qtyRequested);
                 logger.warn(msg);
                 return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Defensive check: qtyPicked should never exceed qtyRequested (business/data rule)
         if (qtyRequested != null && qtyPicked != null && qtyPicked > qtyRequested) {
             String msg = String.format("PickLedger %s qtyPicked (%d) exceeds qtyRequested (%d)",
                     entity.getId(), qtyPicked, qtyRequested);
             logger.error(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // No failing conditions detected
         return EvaluationOutcome.success();
    }
}