package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Criterion for validating that an order can be placed.
 * Used in the place_order transition from initial_state to placed.
 */
@Component
public class OrderValidityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidityCriterion.class);
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    public OrderValidityCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking order validity for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrder)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome validateOrder(Order order) {
        return validateOrderExists(order)
            .and(validatePetId(order))
            .and(validateQuantity(order))
            .and(validateUserId(order));
    }

    private EvaluationOutcome validateOrderExists(Order order) {
        if (order == null) {
            return EvaluationOutcome.Fail.businessRuleFailure("Order entity is null");
        }
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePetId(Order order) {
        if (order.getPetId() == null) {
            return EvaluationOutcome.Fail.businessRuleFailure("Pet ID is required");
        }

        // Note: In a real implementation, we would check if the pet exists
        // and is in 'available' state using entityService.getItem()
        // For now, we assume basic validation
        
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateQuantity(Order order) {
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            return EvaluationOutcome.Fail.businessRuleFailure("Quantity must be positive");
        }
        
        if (order.getQuantity() > 1) {
            return EvaluationOutcome.Fail.businessRuleFailure("Only one pet per order allowed");
        }
        
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateUserId(Order order) {
        // User ID is optional, but if provided, user should exist and be active
        if (order.getUserId() != null) {
            // Note: In a real implementation, we would check if the user exists
            // and is in 'active' state using entityService.getItem()
            // For now, we assume basic validation
        }
        
        return EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "OrderValidityCriterion".equals(opSpec.operationName()) &&
               "Order".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
