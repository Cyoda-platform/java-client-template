package com.java_template.application.criterion;

import com.java_template.application.entity.payment.version_1.Payment;
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

import java.util.Set;

@Component
public class PaymentFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Payment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
         Payment entity = context.entity();

         // Basic allowed statuses according to workflow
         final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "APPROVED", "FAILED", "REFUNDED");

         String status = entity.getStatus();
         String approvedAt = entity.getApprovedAt();
         String orderId = entity.getOrderId();

         // Data quality: status must be one of the expected values
         if (status == null || !ALLOWED_STATUSES.contains(status)) {
             return EvaluationOutcome.fail("Payment.status must be one of PENDING, APPROVED, FAILED or REFUNDED",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rules specific to failure/approval lifecycle:
         // 1) FAILED payments must not have an approval timestamp or an order associated.
         if ("FAILED".equals(status)) {
             if (approvedAt != null && !approvedAt.isBlank()) {
                 return EvaluationOutcome.fail("Failed payment must not have approvedAt set",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (orderId != null && !orderId.isBlank()) {
                 return EvaluationOutcome.fail("Failed payment must not have an orderId associated",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // 2) APPROVED payments must have an approvedAt timestamp (approval time)
         if ("APPROVED".equals(status)) {
             if (approvedAt == null || approvedAt.isBlank()) {
                 return EvaluationOutcome.fail("Approved payment must have approvedAt timestamp",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // 3) PENDING payments must not already have an approval timestamp set
         if ("PENDING".equals(status)) {
             if (approvedAt != null && !approvedAt.isBlank()) {
                 return EvaluationOutcome.fail("Pending payment must not have approvedAt set",
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // 4) REFUNDED payments should normally reference an orderId (best-effort check)
         if ("REFUNDED".equals(status)) {
             if (orderId == null || orderId.isBlank()) {
                 // Not a hard failure but warn about potential data quality issue
                 logger.warn("Refunded payment {} has no associated orderId", entity.getId());
                 return EvaluationOutcome.fail("Refunded payment has no orderId", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If all checks pass, success
         return EvaluationOutcome.success();
    }
}