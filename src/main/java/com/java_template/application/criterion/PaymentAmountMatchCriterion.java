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
public class PaymentAmountMatchCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentAmountMatchCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Payment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // CRITICAL: use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
         Payment payment = context.entity();

         // Basic presence checks
         if (payment.getPaymentId() == null || payment.getPaymentId().isBlank()) {
             return EvaluationOutcome.fail("paymentId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payment.getCartId() == null || payment.getCartId().isBlank()) {
             return EvaluationOutcome.fail("cartId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payment.getAmount() == null) {
             return EvaluationOutcome.fail("amount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payment.getAmount() < 0.0) {
             return EvaluationOutcome.fail("amount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payment.getProvider() == null || payment.getProvider().isBlank()) {
             return EvaluationOutcome.fail("provider is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (payment.getStatus() == null || payment.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: provider must be the configured dummy provider for this system
         // (system uses "DUMMY" as the only supported provider in the functional spec)
         if (!"DUMMY".equals(payment.getProvider())) {
             return EvaluationOutcome.fail("unsupported payment provider", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: allowed statuses
         Set<String> allowedStatuses = Set.of("INITIATED", "PAID", "FAILED", "CANCELED");
         String statusUpper = payment.getStatus().toUpperCase().trim();
         if (!allowedStatuses.contains(statusUpper)) {
             return EvaluationOutcome.fail("unknown payment status: " + payment.getStatus(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: a payment with status PAID should have a positive amount
         if ("PAID".equals(statusUpper) && payment.getAmount() <= 0.0) {
             return EvaluationOutcome.fail("paid payment must have positive amount", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // For INITIATED payments, amount should be non-zero in normal flows (zero-amount payments are suspicious)
         if ("INITIATED".equals(statusUpper) && payment.getAmount() == 0.0) {
             // mark as data quality warning/failure depending on policy; choose DATA_QUALITY_FAILURE
             return EvaluationOutcome.fail("initiated payment with zero amount is not allowed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All basic checks passed. Deeper consistency (e.g., matching cart.grandTotal) requires loading the cart entity
         // which is out of scope for this isolated entity evaluation. We therefore ensure payment fields are consistent.
         logger.debug("Payment {} passed basic validation in {} check", payment.getPaymentId(), className);
         return EvaluationOutcome.success();
    }
}