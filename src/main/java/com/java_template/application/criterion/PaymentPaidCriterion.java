package com.java_template.application.criterion;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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
        logger.info("Checking PaymentPaidCriterion for request: {}", request.getId());
        
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entity();
        
        logger.info("Validating payment for paid state: {}", payment != null ? payment.getPaymentId() : "null");

        // Check if payment entity exists
        if (payment == null) {
            logger.warn("Payment entity not found");
            return EvaluationOutcome.fail("Payment entity not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Note: State validation is handled by the workflow system
        // This criterion focuses on business logic validation

        // Check if payment.amount > 0
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Payment amount must be greater than zero. Amount: {}", payment.getAmount());
            return EvaluationOutcome.fail("Payment amount must be greater than zero", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if payment.provider equals "DUMMY"
        if (!"DUMMY".equals(payment.getProvider())) {
            logger.warn("Invalid payment provider. Expected: DUMMY, Actual: {}", payment.getProvider());
            return EvaluationOutcome.fail("Invalid payment provider", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.info("Payment validation successful for payment: {}", payment.getPaymentId());
        return EvaluationOutcome.success();
    }
}
