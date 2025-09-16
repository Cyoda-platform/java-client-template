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
 * OrderDeliveryCriterion - Check if order delivery can be confirmed
 * 
 * Transition: confirm_delivery (shipped → delivered)
 * Purpose: Check if order delivery can be confirmed
 */
@Component
public class OrderDeliveryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderDeliveryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order delivery criteria for request: {}", request.getId());
        
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

        // Check if order is null (structural validation)
        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!order.isValid()) {
            logger.warn("Order is not valid");
            return EvaluationOutcome.fail("Order is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // 1. Check if shipDate is set and is in the past
        if (order.getShipDate() == null) {
            logger.warn("Order has no ship date: {}", order.getOrderId());
            return EvaluationOutcome.fail("Order must have a ship date", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        LocalDateTime now = LocalDateTime.now();
        if (order.getShipDate().isAfter(now)) {
            logger.warn("Order ship date is in the future: {} for order {}", order.getShipDate(), order.getOrderId());
            return EvaluationOutcome.fail("Order ship date cannot be in the future", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 2. Verify sufficient time has passed since shipping (at least 1 day)
        long daysSinceShipping = ChronoUnit.DAYS.between(order.getShipDate(), now);
        if (daysSinceShipping < 1) {
            logger.warn("Insufficient time since shipping: {} days for order {}", daysSinceShipping, order.getOrderId());
            return EvaluationOutcome.fail("At least 1 day must pass since shipping before delivery confirmation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 3. Check if delivery confirmation is available in context (simplified - assume always available)
        // In a real system, this would check context for delivery confirmation data
        
        // 4. Validate delivery address matches shipping address (simplified - assume always matches)
        // In a real system, this would compare delivery address from context with shipping address

        logger.debug("Order delivery validation passed for order: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
