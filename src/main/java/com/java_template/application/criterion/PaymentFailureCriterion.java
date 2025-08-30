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

         if (entity == null) {
             logger.warn("Payment entity is null in PaymentFailureCriterion");
             return EvaluationOutcome.fail("Payment entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required field validations (use only existing getters)
         if (entity.getPaymentId() == null || entity.getPaymentId().isBlank()) {
             return EvaluationOutcome.fail("paymentId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCartId() == null || entity.getCartId().isBlank()) {
             return EvaluationOutcome.fail("cartId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getProvider() == null || entity.getProvider().isBlank()) {
             return EvaluationOutcome.fail("provider is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAmount() == null) {
             return EvaluationOutcome.fail("amount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAmount() < 0) {
             return EvaluationOutcome.fail("amount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Ensure status is one of the expected domain values
         Set<String> validStatuses = Set.of("INITIATED", "PAID", "FAILED", "CANCELED");
         if (!validStatuses.contains(entity.getStatus().toUpperCase())) {
             return EvaluationOutcome.fail("Unknown payment status: " + entity.getStatus(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: if payment is FAILED, surface as a business rule failure so downstream processors/ops can act
         if ("FAILED".equalsIgnoreCase(entity.getStatus())) {
             String msg = String.format("Payment %s for cart %s is marked FAILED", entity.getPaymentId(), entity.getCartId());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Additional business sanity: a PAID or INITIATED payment should have a positive amount
         if (("PAID".equalsIgnoreCase(entity.getStatus()) || "INITIATED".equalsIgnoreCase(entity.getStatus())) && entity.getAmount() <= 0) {
             return EvaluationOutcome.fail("Payment amount must be greater than zero for initiated/paid payments", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}