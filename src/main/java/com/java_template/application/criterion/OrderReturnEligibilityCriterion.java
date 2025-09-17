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
 * OrderReturnEligibilityCriterion - Validates if a delivered order is eligible for return
 * 
 * Transition: return_order
 * Purpose: Validates order return eligibility within timeframe
 */
@Component
public class OrderReturnEligibilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderReturnEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order return eligibility criteria for request: {}", request.getId());
        
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
     * Main validation logic for order return eligibility
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify order is in delivered state
        if (!"delivered".equals(currentState)) {
            logger.warn("Order {} is not in delivered state, current state: {}", order.getOrderId(), currentState);
            return EvaluationOutcome.fail("Order is not in delivered state for return", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check order validity
        if (!order.isValid()) {
            logger.warn("Order {} is not valid", order.getOrderId());
            return EvaluationOutcome.fail("Order data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if return is within allowed timeframe (30 days from delivery)
        if (order.getShipDate() != null) {
            long daysSinceDelivery = java.time.Duration.between(order.getShipDate(), java.time.LocalDateTime.now()).toDays();
            if (daysSinceDelivery > 30) {
                logger.warn("Order {} return period has expired ({} days since delivery)", order.getOrderId(), daysSinceDelivery);
                return EvaluationOutcome.fail("Order return period has expired (30 days limit)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } else {
            logger.warn("Order {} has no delivery date recorded", order.getOrderId());
            return EvaluationOutcome.fail("Order delivery date is required for return validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate customer information is still available
        if (order.getCustomerInfo() == null) {
            logger.warn("Order {} has no customer information for return processing", order.getOrderId());
            return EvaluationOutcome.fail("Customer information is required for return processing", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if order was completed successfully
        if (order.getComplete() == null || !order.getComplete()) {
            logger.warn("Order {} was not completed successfully", order.getOrderId());
            return EvaluationOutcome.fail("Order was not completed successfully", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Order {} is eligible for return", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
