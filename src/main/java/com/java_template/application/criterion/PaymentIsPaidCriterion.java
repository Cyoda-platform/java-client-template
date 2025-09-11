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

/**
 * PaymentIsPaidCriterion - Checks if payment is in PAID state.
 * 
 * This criterion is used to validate payment before order creation and prevent
 * order creation from unpaid carts. It evaluates the payment state from metadata.
 */
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
        logger.debug("Checking Payment is paid criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePaymentIsPaid)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the payment is in PAID state
     */
    private EvaluationOutcome validatePaymentIsPaid(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entityWithMetadata().entity();

        // Check if payment is null (structural validation)
        if (payment == null) {
            logger.warn("Payment is null");
            return EvaluationOutcome.fail("Payment is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Get payment state from metadata
        String paymentState = context.entityWithMetadata().metadata().getState();
        
        if (paymentState == null) {
            logger.warn("Payment state is null");
            return EvaluationOutcome.fail("Payment state is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if payment is in PAID state
        if (!"paid".equals(paymentState.toLowerCase())) {
            logger.debug("Payment is not in PAID state: {}", paymentState);
            return EvaluationOutcome.fail("Payment is not paid (current state: " + paymentState + ")", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Payment is paid validation passed");
        return EvaluationOutcome.success();
    }
}
