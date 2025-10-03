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

        if (order == null) {
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        logger.debug("Checking payment confirmation for order: {}", order.getOrderId());

        try {
            // Check if payment information exists
            if (order.getPayment() == null) {
                logger.debug("No payment information found for order: {}", order.getOrderId());
                return EvaluationOutcome.fail("Payment information is missing",
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            Order.Payment payment = order.getPayment();

            // Check payment status
            if (!"captured".equals(payment.getStatus()) && !"authorized".equals(payment.getStatus())) {
                logger.debug("Payment not confirmed for order: {} - Status: {}",
                           order.getOrderId(), payment.getStatus());
                return EvaluationOutcome.fail("Payment not confirmed - Status: " + payment.getStatus(),
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            logger.info("Payment confirmation passed for order: {}", order.getOrderId());
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error during payment confirmation check for order: {}", order.getOrderId(), e);
            return EvaluationOutcome.fail("Payment validation error: " + e.getMessage(),
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
