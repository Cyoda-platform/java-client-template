package com.java_template.application.criterion;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public PaymentValidCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating PaymentValidCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePayment)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePayment(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entity();
        
        logger.info("Validating payment criteria for payment: {}", payment != null ? payment.getPaymentId() : "null");

        if (payment == null) {
            return EvaluationOutcome.fail("Payment entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check payment.cartId is not null/empty
        if (payment.getCartId() == null || payment.getCartId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Payment cart reference is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check payment.amount > 0
        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            return EvaluationOutcome.fail("Payment amount must be greater than zero", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check payment.provider == "DUMMY"
        if (!"DUMMY".equals(payment.getProvider())) {
            return EvaluationOutcome.fail("Invalid payment provider: " + payment.getProvider(), StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            // Check referenced cart exists
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, payment.getCartId());
            Cart cart = cartResponse.getData();
            
            if (cart == null) {
                return EvaluationOutcome.fail("Referenced cart not found: " + payment.getCartId(), StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check referenced cart.grandTotal == payment.amount
            if (cart.getGrandTotal() == null || !cart.getGrandTotal().equals(payment.getAmount())) {
                return EvaluationOutcome.fail("Payment amount does not match cart total: payment=" + 
                    payment.getAmount() + ", cart=" + cart.getGrandTotal(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            logger.info("Payment validation passed: paymentId={}, cartId={}, amount={}", 
                payment.getPaymentId(), payment.getCartId(), payment.getAmount());

            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Failed to validate payment: {}", e.getMessage());
            return EvaluationOutcome.fail("Failed to validate payment: " + e.getMessage(), StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
