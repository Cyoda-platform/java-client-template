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
 * Criterion to check if payment has been confirmed for an order
 * Validates payment status and transaction details
 */
@Component
public class PaymentConfirmationCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(PaymentConfirmationCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public PaymentConfirmationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

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
        
        logger.debug("Checking payment confirmation for order: {}", order.getOrderId());

        try {
            // Check if payment information exists
            if (order.getPayment() == null) {
                logger.debug("No payment information found for order: {}", order.getOrderId());
                return false;
            }

            Order.Payment payment = order.getPayment();

            // Check payment status
            if (!isPaymentConfirmed(payment)) {
                logger.debug("Payment not confirmed for order: {} - Status: {}", 
                           order.getOrderId(), payment.getStatus());
                return false;
            }

            // Validate payment amount matches order total
            if (!validatePaymentAmount(order, payment)) {
                logger.debug("Payment amount mismatch for order: {}", order.getOrderId());
                return false;
            }

            // Check transaction reference
            if (!validateTransactionReference(payment)) {
                logger.debug("Invalid transaction reference for order: {}", order.getOrderId());
                return false;
            }

            // Check payment method
            if (!validatePaymentMethod(payment)) {
                logger.debug("Invalid payment method for order: {}", order.getOrderId());
                return false;
            }

            logger.info("Payment confirmation passed for order: {}", order.getOrderId());
            return true;

        } catch (Exception e) {
            logger.error("Error during payment confirmation check for order: {}", order.getOrderId(), e);
            return false;
        }
    }

    private boolean isPaymentConfirmed(Order.Payment payment) {
        String status = payment.getStatus();
        
        // Payment is confirmed if it's captured or authorized
        return "captured".equals(status) || "authorized".equals(status);
    }

    private boolean validatePaymentAmount(Order order, Order.Payment payment) {
        // Check if payment amount is set
        if (payment.getAmount() == null) {
            logger.debug("Payment amount is null");
            return false;
        }

        // Check if order total is set
        if (order.getTotalAmount() == null) {
            logger.debug("Order total amount is null");
            return false;
        }

        // Payment amount should match order total (allow small rounding differences)
        double difference = Math.abs(payment.getAmount() - order.getTotalAmount());
        if (difference > 0.01) {
            logger.debug("Payment amount ({}) does not match order total ({})", 
                        payment.getAmount(), order.getTotalAmount());
            return false;
        }

        // Check if payment amount is positive
        if (payment.getAmount() <= 0) {
            logger.debug("Payment amount must be positive: {}", payment.getAmount());
            return false;
        }

        return true;
    }

    private boolean validateTransactionReference(Order.Payment payment) {
        // Transaction reference should exist for confirmed payments
        if (payment.getTransactionRef() == null || payment.getTransactionRef().trim().isEmpty()) {
            logger.debug("Transaction reference is missing");
            return false;
        }

        // Basic format validation (should start with TXN- for our system)
        if (!payment.getTransactionRef().startsWith("TXN-")) {
            logger.debug("Invalid transaction reference format: {}", payment.getTransactionRef());
            return false;
        }

        return true;
    }

    private boolean validatePaymentMethod(Order.Payment payment) {
        String method = payment.getMethod();
        
        // Payment method should be specified
        if (method == null || method.trim().isEmpty()) {
            logger.debug("Payment method is missing");
            return false;
        }

        // Validate against allowed payment methods
        return isValidPaymentMethod(method);
    }

    private boolean isValidPaymentMethod(String method) {
        // List of valid payment methods
        return "credit_card".equals(method) ||
               "debit_card".equals(method) ||
               "paypal".equals(method) ||
               "bank_transfer".equals(method) ||
               "apple_pay".equals(method) ||
               "google_pay".equals(method) ||
               "cash".equals(method);
    }

    /**
     * Additional validation for specific payment statuses
     */
    private boolean validatePaymentTimestamps(Order.Payment payment) {
        String status = payment.getStatus();
        
        if ("authorized".equals(status)) {
            // Authorized payments should have authorization timestamp
            if (payment.getAuthorizedAt() == null) {
                logger.debug("Authorized payment missing authorization timestamp");
                return false;
            }
        }
        
        if ("captured".equals(status)) {
            // Captured payments should have both authorization and capture timestamps
            if (payment.getAuthorizedAt() == null || payment.getCapturedAt() == null) {
                logger.debug("Captured payment missing required timestamps");
                return false;
            }
            
            // Capture timestamp should be after authorization timestamp
            if (payment.getCapturedAt().isBefore(payment.getAuthorizedAt())) {
                logger.debug("Capture timestamp is before authorization timestamp");
                return false;
            }
        }
        
        return true;
    }

    /**
     * Check if payment has failed
     */
    private boolean isPaymentFailed(Order.Payment payment) {
        return "failed".equals(payment.getStatus()) || 
               "capture_failed".equals(payment.getStatus()) ||
               "declined".equals(payment.getStatus());
    }
}
