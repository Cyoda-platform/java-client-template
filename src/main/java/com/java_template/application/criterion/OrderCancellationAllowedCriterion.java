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
 * OrderCancellationAllowedCriterion - Determines if an approved order can still be cancelled
 * 
 * Transition: cancel_approved_order
 * Purpose: Validates if approved order can still be cancelled
 */
@Component
public class OrderCancellationAllowedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderCancellationAllowedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order cancellation allowed criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for order cancellation allowance
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify order is in approved state
        if (!"approved".equals(currentState)) {
            logger.warn("Order {} is not in approved state, current state: {}", order.getOrderId(), currentState);
            return EvaluationOutcome.fail("Order is not in approved state for cancellation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check order validity
        if (!order.isValid()) {
            logger.warn("Order {} is not valid", order.getOrderId());
            return EvaluationOutcome.fail("Order data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if order has not been shipped yet (no ship date)
        if (order.getShipDate() != null) {
            logger.warn("Order {} has already been shipped and cannot be cancelled", order.getOrderId());
            return EvaluationOutcome.fail("Order has already been shipped and cannot be cancelled", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if order is marked as complete
        if (order.getComplete() != null && order.getComplete()) {
            logger.warn("Order {} is already complete and cannot be cancelled", order.getOrderId());
            return EvaluationOutcome.fail("Order is already complete and cannot be cancelled", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check cancellation is within allowed timeframe (24 hours from approval)
        if (order.getUpdatedAt() != null) {
            long hoursSinceApproval = java.time.Duration.between(order.getUpdatedAt(), java.time.LocalDateTime.now()).toHours();
            if (hoursSinceApproval > 24) {
                logger.warn("Order {} cancellation period has expired ({} hours since approval)", order.getOrderId(), hoursSinceApproval);
                return EvaluationOutcome.fail("Order cancellation period has expired (24 hours limit)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        logger.debug("Order {} can be cancelled", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
