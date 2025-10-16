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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ABOUTME: Criterion that validates payment information for order processing,
 * checking payment method and amount are valid before order preparation.
 */
@Component
public class PaymentValidityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(PaymentValidityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public PaymentValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking payment validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Order.class, this::validatePaymentValidity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if the payment information is valid
     */
    private EvaluationOutcome validatePaymentValidity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        logger.debug("Evaluating payment validity for order: {} in state: {}", order.getOrderId(), currentState);

        // Check if entity is null (structural validation)
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!order.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Order entity is not valid");
            return EvaluationOutcome.fail("Order entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Order must be in confirmed state to be prepared
        if (!"confirmed".equals(currentState)) {
            logger.warn("Order {} is not in confirmed state: {}", order.getOrderId(), currentState);
            return EvaluationOutcome.fail("Order must be in confirmed state to be prepared", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if payment information exists
        if (order.getPayment() == null) {
            logger.warn("Order {} does not have payment information", order.getOrderId());
            return EvaluationOutcome.fail("Order must have payment information", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if payment method is specified
        if (order.getPayment().getMethod() == null || order.getPayment().getMethod().trim().isEmpty()) {
            logger.warn("Order {} does not have a valid payment method", order.getOrderId());
            return EvaluationOutcome.fail("Order must have a valid payment method", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if pricing information exists and is valid
        if (order.getPricing() == null || order.getPricing().getTotalAmount() == null || order.getPricing().getTotalAmount() <= 0) {
            logger.warn("Order {} does not have valid pricing information", order.getOrderId());
            return EvaluationOutcome.fail("Order must have valid pricing information", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Order {} payment validity check passed", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
