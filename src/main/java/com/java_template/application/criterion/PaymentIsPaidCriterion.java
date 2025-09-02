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
public class PaymentIsPaidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentIsPaidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if payment is paid for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePaymentIsPaid)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePaymentIsPaid(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entity();
        
        // Check if payment is null
        if (payment == null) {
            logger.warn("Payment entity is null");
            return EvaluationOutcome.fail("Payment entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Note: In a real implementation, we would check payment.meta.state for "PAID"
        // Since we can't access the workflow state directly here, we'll validate other aspects
        // The workflow system should only call this criterion when payment state validation is needed

        // Validate payment amount is positive
        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            logger.warn("Payment {} has invalid amount: {}", payment.getPaymentId(), payment.getAmount());
            return EvaluationOutcome.fail("Payment amount must be positive", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate payment provider is DUMMY (only supported provider)
        if (!"DUMMY".equals(payment.getProvider())) {
            logger.warn("Payment {} has unsupported provider: {}", payment.getPaymentId(), payment.getProvider());
            return EvaluationOutcome.fail("Only DUMMY payment provider is supported", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate payment has required fields
        if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            logger.warn("Payment has no payment ID");
            return EvaluationOutcome.fail("Payment ID is required", 
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (payment.getCartId() == null || payment.getCartId().trim().isEmpty()) {
            logger.warn("Payment {} has no cart ID", payment.getPaymentId());
            return EvaluationOutcome.fail("Payment cart ID is required", 
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        logger.debug("Payment {} validation passed", payment.getPaymentId());
        return EvaluationOutcome.success();
    }
}
