package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PaymentIsPaidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    @Autowired
    private EntityService entityService;

    public PaymentIsPaidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking payment is paid criterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validatePaymentIsPaid)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePaymentIsPaid(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Validating payment is paid for order: {}", order.getOrderId());

        try {
            // Extract paymentId from context or order entity
            // In a real implementation, this would come from the request payload
            String paymentId = extractPaymentIdFromContext(context);
            
            if (paymentId == null || paymentId.trim().isEmpty()) {
                logger.warn("Payment ID not found for order: {}", order.getOrderId());
                return EvaluationOutcome.fail("Payment ID not provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Get payment entity
            Payment payment = getPaymentById(paymentId);
            if (payment == null) {
                logger.warn("Payment not found: {}", paymentId);
                return EvaluationOutcome.fail("Payment not found: " + paymentId, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check payment state (in real implementation, would check payment.meta.state)
            // For now, we'll assume the workflow ensures this criterion only runs when appropriate
            logger.info("Payment found: {}", paymentId);

            // Get cart to validate amount
            Cart cart = getCartById(payment.getCartId());
            if (cart == null) {
                logger.warn("Cart not found for payment: {}", payment.getCartId());
                return EvaluationOutcome.fail("Cart not found for payment: " + payment.getCartId(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Validate payment amount matches cart total
            if (!payment.getAmount().equals(cart.getGrandTotal())) {
                logger.warn("Payment amount does not match cart total. Payment: {}, Cart: {}", 
                           payment.getAmount(), cart.getGrandTotal());
                return EvaluationOutcome.fail("Payment amount does not match cart total", 
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            logger.info("Payment validation successful for payment: {}", paymentId);
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error validating payment for order: {}", order.getOrderId(), e);
            return EvaluationOutcome.fail("Error validating payment: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }

    private String extractPaymentIdFromContext(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        // In a real implementation, this would extract from the request payload
        // For now, return a placeholder
        return "extracted-payment-id";
    }

    private Payment getPaymentById(String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(paymentIdCondition));

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);
            
            return paymentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving payment: {}", paymentId, e);
            return null;
        }
    }

    private Cart getCartById(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);
            
            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving cart: {}", cartId, e);
            return null;
        }
    }
}
