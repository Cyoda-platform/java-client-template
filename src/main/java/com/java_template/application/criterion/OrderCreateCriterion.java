package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
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

import java.util.Map;

@Component
public class OrderCreateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public OrderCreateCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating OrderCreateCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderCreation)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrderCreation(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();

        logger.info("Validating order creation criteria for order: {}", order != null ? order.getOrderId() : "null");

        // Extract payment and cart references from request data - for now using hardcoded values
        // In a real implementation, this would come from the request payload
        String paymentId = "pay_sample"; // TODO: Extract from request payload
        String cartId = "cart_sample"; // TODO: Extract from request payload

        if (paymentId == null || paymentId.trim().isEmpty()) {
            return EvaluationOutcome.fail("Payment ID is required for order creation", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (cartId == null || cartId.trim().isEmpty()) {
            return EvaluationOutcome.fail("Cart ID is required for order creation", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            // Check payment is in PAID state
            EntityResponse<Payment> paymentResponse = entityService.findByBusinessId(Payment.class, paymentId);
            Payment payment = paymentResponse.getData();
            
            if (payment == null) {
                return EvaluationOutcome.fail("Payment not found for ID: " + paymentId, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            String paymentState = paymentResponse.getMetadata().getState();
            if (!"PAID".equals(paymentState)) {
                return EvaluationOutcome.fail("Payment is not in PAID state: " + paymentState, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Check cart is in CONVERTED state
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId);
            Cart cart = cartResponse.getData();
            
            if (cart == null) {
                return EvaluationOutcome.fail("Cart not found for ID: " + cartId, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            String cartState = cartResponse.getMetadata().getState();
            if (!"CONVERTED".equals(cartState)) {
                return EvaluationOutcome.fail("Cart is not in CONVERTED state: " + cartState, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Check cart has valid lines (not empty)
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                return EvaluationOutcome.fail("Cart has no items", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check cart has valid guestContact with required fields
            if (cart.getGuestContact() == null || !cart.getGuestContact().isValid()) {
                return EvaluationOutcome.fail("Guest contact information is incomplete", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check cart.grandTotal > 0
            if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
                return EvaluationOutcome.fail("Cart total is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Verify payment amount matches cart total
            if (!payment.getAmount().equals(cart.getGrandTotal())) {
                return EvaluationOutcome.fail("Payment amount does not match cart total", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            logger.info("Order creation validation passed: paymentId={}, cartId={}, amount={}", 
                paymentId, cartId, payment.getAmount());

            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Failed to validate order creation: {}", e.getMessage());
            return EvaluationOutcome.fail("Failed to validate order creation: " + e.getMessage(), StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
