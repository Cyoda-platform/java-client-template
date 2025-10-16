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
 * ABOUTME: Criterion that checks if an order is ready for shipment,
 * validating order is confirmed and preparation is complete before shipping.
 */
@Component
public class ShipmentReadinessCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentReadinessCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public ShipmentReadinessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking shipment readiness for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Order.class, this::validateShipmentReadiness)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if the order is ready for shipment
     */
    private EvaluationOutcome validateShipmentReadiness(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        logger.debug("Evaluating shipment readiness for order: {} in state: {}", order.getOrderId(), currentState);

        // Check if entity is null (structural validation)
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!order.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Order entity is not valid");
            return EvaluationOutcome.fail("Order entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Order must be in preparing state to be shipped
        if (!"preparing".equals(currentState)) {
            logger.warn("Order {} is not in preparing state: {}", order.getOrderId(), currentState);
            return EvaluationOutcome.fail("Order must be in preparing state to be shipped", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if shipping information exists
        if (order.getShipping() == null) {
            logger.warn("Order {} does not have shipping information", order.getOrderId());
            return EvaluationOutcome.fail("Order must have shipping information", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if shipping method is specified
        if (order.getShipping().getMethod() == null || order.getShipping().getMethod().trim().isEmpty()) {
            logger.warn("Order {} does not have a valid shipping method", order.getOrderId());
            return EvaluationOutcome.fail("Order must have a valid shipping method", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if shipping address is provided for delivery orders
        if ("delivery".equalsIgnoreCase(order.getShipping().getMethod()) && order.getShipping().getAddress() == null) {
            logger.warn("Order {} requires shipping address for delivery method", order.getOrderId());
            return EvaluationOutcome.fail("Delivery orders must have a shipping address", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Order {} shipment readiness check passed", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
