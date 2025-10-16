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
 * ABOUTME: Criterion that validates if an order can be processed,
 * checking customer is active and pet is available for the order.
 */
@Component
public class OrderValidityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public OrderValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking order validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Order.class, this::validateOrderValidity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if the order can be processed
     */
    private EvaluationOutcome validateOrderValidity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        logger.debug("Evaluating validity for order: {} in state: {}", order.getOrderId(), currentState);

        // Check if entity is null (structural validation)
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!order.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Order entity is not valid");
            return EvaluationOutcome.fail("Order entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Order must be in placed state to be confirmed
        if (!"placed".equals(currentState)) {
            logger.warn("Order {} is not in placed state: {}", order.getOrderId(), currentState);
            return EvaluationOutcome.fail("Order must be in placed state to be confirmed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if order has required customer and pet references
        if (order.getCustomerId() == null || order.getCustomerId().trim().isEmpty()) {
            logger.warn("Order {} does not have a valid customer ID", order.getOrderId());
            return EvaluationOutcome.fail("Order must have a valid customer ID", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (order.getPetId() == null || order.getPetId().trim().isEmpty()) {
            logger.warn("Order {} does not have a valid pet ID", order.getOrderId());
            return EvaluationOutcome.fail("Order must have a valid pet ID", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check quantity is valid
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            logger.warn("Order {} has invalid quantity: {}", order.getOrderId(), order.getQuantity());
            return EvaluationOutcome.fail("Order must have a valid quantity", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Order {} validity check passed", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
