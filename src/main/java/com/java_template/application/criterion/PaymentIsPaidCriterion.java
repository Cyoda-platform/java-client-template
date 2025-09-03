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
        logger.info("Checking payment is paid for request: {}", request.getId());
        
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
        EntityCriteriaCalculationRequest request = context.request();

        logger.debug("Validating payment is paid: {}", payment != null ? payment.getPaymentId() : "null");

        // CRITICAL: Use payment getters directly - never extract from payload
        
        // 1. Validate payment entity exists
        if (payment == null) {
            logger.warn("Payment entity is null");
            return EvaluationOutcome.fail("Payment is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // 2. Check payment state using entity.meta.state
        // Note: In the Cyoda system, the state is managed by the workflow and accessed via request metadata
        String paymentState = null;
        if (request.getPayload() != null && request.getPayload().getMeta() != null) {
            Object stateObj = request.getPayload().getMeta().get("state");
            if (stateObj != null) {
                paymentState = stateObj.toString();
            }
        }
        
        if (paymentState == null) {
            logger.warn("Payment {} has no state information", payment.getPaymentId());
            return EvaluationOutcome.fail("Payment state is unknown", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // 3. Validate state equals "PAID"
        if (!"PAID".equals(paymentState)) {
            logger.warn("Payment {} is not in PAID state: {}", payment.getPaymentId(), paymentState);
            return EvaluationOutcome.fail("Payment is not in PAID state: " + paymentState, 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 4. Check payment.amount > 0
        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            logger.warn("Payment {} has invalid amount: {}", payment.getPaymentId(), payment.getAmount());
            return EvaluationOutcome.fail("Payment amount is zero or negative", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 5. Validate payment.provider equals "DUMMY"
        if (!"DUMMY".equals(payment.getProvider())) {
            logger.warn("Payment {} has invalid provider: {}", payment.getPaymentId(), payment.getProvider());
            return EvaluationOutcome.fail("Payment provider is not DUMMY: " + payment.getProvider(), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Additional validation for payment completeness
        if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            logger.warn("Payment has missing payment ID");
            return EvaluationOutcome.fail("Payment ID is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (payment.getCartId() == null || payment.getCartId().trim().isEmpty()) {
            logger.warn("Payment {} has missing cart ID", payment.getPaymentId());
            return EvaluationOutcome.fail("Payment cart ID is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.info("Payment {} validation passed: state={}, amount={}, provider={}", 
            payment.getPaymentId(), paymentState, payment.getAmount(), payment.getProvider());
        
        return EvaluationOutcome.success();
    }
}
