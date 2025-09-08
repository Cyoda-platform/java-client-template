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

import java.util.Arrays;
import java.util.List;

/**
 * PaymentCriterion - Validates payment business rules
 * 
 * This criterion validates:
 * - Required fields are present and valid
 * - Payment status is valid
 * - Payment amount is positive
 * - Provider is valid
 * - Cart reference is valid
 */
@Component
public class PaymentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final List<String> VALID_PAYMENT_STATUSES = Arrays.asList(
        "INITIATED", "PAID", "FAILED", "CANCELED"
    );

    private static final List<String> VALID_PROVIDERS = Arrays.asList(
        "DUMMY"
    );

    public PaymentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Payment criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePayment)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the Payment entity
     */
    private EvaluationOutcome validatePayment(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entityWithMetadata().entity();

        // Check if payment is null (structural validation)
        if (payment == null) {
            logger.warn("Payment is null");
            return EvaluationOutcome.fail("Payment is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Basic entity validation
        if (!payment.isValid()) {
            logger.warn("Payment basic validation failed for paymentId: {}", payment.getPaymentId());
            return EvaluationOutcome.fail("Payment basic validation failed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate payment status
        if (!VALID_PAYMENT_STATUSES.contains(payment.getStatus())) {
            logger.warn("Invalid payment status: {}", payment.getStatus());
            return EvaluationOutcome.fail("Invalid payment status: " + payment.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate provider
        if (!VALID_PROVIDERS.contains(payment.getProvider())) {
            logger.warn("Invalid payment provider: {}", payment.getProvider());
            return EvaluationOutcome.fail("Invalid payment provider: " + payment.getProvider(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate payment amount
        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            logger.warn("Payment amount must be positive: {}", payment.getAmount());
            return EvaluationOutcome.fail("Payment amount must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate payment amount range (reasonable limits)
        if (payment.getAmount() > 100000.0) {
            logger.warn("Payment amount exceeds maximum limit: {}", payment.getAmount());
            return EvaluationOutcome.fail("Payment amount exceeds maximum limit of 100,000", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate payment ID format
        if (payment.getPaymentId() != null && (payment.getPaymentId().length() < 3 || payment.getPaymentId().length() > 50)) {
            logger.warn("Payment ID length invalid: {}", payment.getPaymentId());
            return EvaluationOutcome.fail("Payment ID must be between 3 and 50 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate cart ID format
        if (payment.getCartId() != null && (payment.getCartId().length() < 3 || payment.getCartId().length() > 50)) {
            logger.warn("Cart ID length invalid: {}", payment.getCartId());
            return EvaluationOutcome.fail("Cart ID must be between 3 and 50 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
