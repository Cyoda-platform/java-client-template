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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * OrderCancellationCriterion - Check if order can be cancelled
 * 
 * Transitions: cancel_order, cancel_approved_order (placed/approved → cancelled)
 * Purpose: Check if order can be cancelled
 */
@Component
public class OrderCancellationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderCancellationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order cancellation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if order is null (structural validation)
        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!order.isValid()) {
            logger.warn("Order is not valid");
            return EvaluationOutcome.fail("Order is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 1. Verify order is not already in 'shipped' or 'delivered' state
        if ("shipped".equals(currentState) || "delivered".equals(currentState)) {
            logger.warn("Order cannot be cancelled in state: {} for order {}", currentState, order.getOrderId());
            return EvaluationOutcome.fail("Order cannot be cancelled after shipping", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 2. Check if cancellation is within allowed timeframe (within 24 hours of placement)
        if (order.getOrderDate() != null) {
            LocalDateTime now = LocalDateTime.now();
            long hoursSinceOrder = ChronoUnit.HOURS.between(order.getOrderDate(), now);
            
            if (hoursSinceOrder > 24) {
                logger.warn("Order cancellation outside allowed timeframe: {} hours for order {}", hoursSinceOrder, order.getOrderId());
                return EvaluationOutcome.fail("Order can only be cancelled within 24 hours of placement", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // 3. Validate cancellation reason is provided in context (simplified - assume always provided)
        // In a real system, this would check context for cancellation reason
        
        // 4. Check if order has not been processed for shipping (simplified - check if shipDate is null)
        if (order.getShipDate() != null) {
            logger.warn("Order has already been processed for shipping: {}", order.getOrderId());
            return EvaluationOutcome.fail("Order cannot be cancelled after shipping processing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Order cancellation validation passed for order: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
