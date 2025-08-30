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

@Component
public class PaymentPaidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentPaidCriterion(SerializerFactory serializerFactory) {
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

         if (entity == null) {
             return EvaluationOutcome.fail("Payment entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required identifiers and links
         if (entity.getPaymentId() == null || entity.getPaymentId().isBlank()) {
             return EvaluationOutcome.fail("paymentId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCartId() == null || entity.getCartId().isBlank()) {
             return EvaluationOutcome.fail("cartId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Amount validation
         if (entity.getAmount() == null) {
             return EvaluationOutcome.fail("amount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAmount() < 0) {
             return EvaluationOutcome.fail("amount must not be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Provider presence (expected for demo)
         if (entity.getProvider() == null || entity.getProvider().isBlank()) {
             return EvaluationOutcome.fail("provider is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Status must be present
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: payment must be in PAID status to be considered complete
         if (!"PAID".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("payment is not in PAID status", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}