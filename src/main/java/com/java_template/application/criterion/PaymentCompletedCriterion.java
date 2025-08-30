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
public class PaymentCompletedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentCompletedCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
         Payment payment = context.entity();

         if (payment == null) {
             logger.debug("Payment entity is null in context");
             return EvaluationOutcome.fail("Payment entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (payment.getPaymentId() == null || payment.getPaymentId().isBlank()) {
             return EvaluationOutcome.fail("paymentId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (payment.getCartId() == null || payment.getCartId().isBlank()) {
             return EvaluationOutcome.fail("cartId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (payment.getAmount() == null || payment.getAmount() <= 0) {
             return EvaluationOutcome.fail("Invalid payment amount", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (payment.getProvider() == null || payment.getProvider().isBlank()) {
             return EvaluationOutcome.fail("Payment provider is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: payment must be marked as PAID to be considered completed
         String status = payment.getStatus();
         if (status == null || !status.equalsIgnoreCase("PAID")) {
             return EvaluationOutcome.fail("Payment not completed (status != PAID)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}