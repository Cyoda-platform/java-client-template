package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
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
 * ABOUTME: PaymentValidationCriterion validates payment information and authorization
 * before allowing transition from Submitted to Paid state.
 */
@Component
public class PaymentValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Payment validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validatePayment)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePayment(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        // Check if order is null (structural validation)
        if (order == null) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "Order entity is null");
        }

        // Validate payment information exists
        if (order.getPayment() == null) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "Payment information is required");
        }

        Order.Payment payment = order.getPayment();

        // Validate payment method
        if (payment.getMethod() == null || payment.getMethod().trim().isEmpty()) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "Payment method is required");
        }

        if (!isValidPaymentMethod(payment.getMethod())) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                        "Invalid payment method: " + payment.getMethod());
        }

        // Validate payment amount
        if (payment.getAmount() == null) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "Payment amount is required");
        }

        if (payment.getAmount() < 0) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                        "Payment amount cannot be negative");
        }

        // Validate payment amount matches order total
        Double orderTotal = order.getOrderTotal();
        if (!payment.getAmount().equals(orderTotal)) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                        "Payment amount (" + payment.getAmount() + 
                                        ") does not match order total (" + orderTotal + ")");
        }

        // Validate currency
        if (payment.getCurrency() == null || payment.getCurrency().trim().isEmpty()) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "Payment currency is required");
        }

        if (!isValidCurrency(payment.getCurrency())) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                        "Invalid currency: " + payment.getCurrency());
        }

        // Validate payment status
        if (payment.getStatus() == null || payment.getStatus().trim().isEmpty()) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "Payment status is required");
        }

        // For payment processing, status should be pending or authorized
        if (!isValidPaymentStatus(payment.getStatus())) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                        "Invalid payment status for processing: " + payment.getStatus());
        }

        // If payment is already failed, cannot proceed
        if ("failed".equals(payment.getStatus())) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                        "Cannot process failed payment");
        }

        // Validate transaction reference if payment is authorized
        if ("authorized".equals(payment.getStatus()) && 
            (payment.getTransactionRef() == null || payment.getTransactionRef().trim().isEmpty())) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                        "Transaction reference is required for authorized payments");
        }

        logger.debug("Payment validation passed for orderId: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }

    private boolean isValidPaymentMethod(String method) {
        return "credit_card".equals(method) || 
               "debit_card".equals(method) || 
               "paypal".equals(method) || 
               "bank_transfer".equals(method) || 
               "cash".equals(method) ||
               "gift_card".equals(method);
    }

    private boolean isValidCurrency(String currency) {
        // Support common currencies
        return "USD".equals(currency) || 
               "EUR".equals(currency) || 
               "GBP".equals(currency) || 
               "CAD".equals(currency) ||
               "AUD".equals(currency);
    }

    private boolean isValidPaymentStatus(String status) {
        return "pending".equals(status) || 
               "authorized".equals(status) || 
               "captured".equals(status);
    }
}
