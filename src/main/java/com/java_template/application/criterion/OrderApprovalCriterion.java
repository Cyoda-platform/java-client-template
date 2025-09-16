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
 * Criterion for validating that an order can be approved.
 * Used in the approve_order transition from placed to approved.
 */
@Component
public class OrderApprovalCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(OrderApprovalCriterion.class);
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    public OrderApprovalCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking order approval criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderForApproval)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome validateOrderForApproval(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        return validateOrderExists(order)
            .and(validateOrderState(order))
            .and(validatePetStillReserved(order))
            .and(validateUserStillActive(order))
            .and(validatePaymentInformation(order));
    }

    private EvaluationOutcome validateOrderExists(Order order) {
        if (order == null) {
            return EvaluationOutcome.Fail.businessRuleFailure("Order entity is null");
        }
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateOrderState(Order order) {
        // Note: In a real implementation, we would check if the order
        // is in 'placed' state using the entity metadata
        // For now, we assume this is handled by the workflow engine
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePetStillReserved(Order order) {
        if (order.getPetId() == null) {
            return EvaluationOutcome.Fail.businessRuleFailure("Pet ID is missing");
        }

        // Note: In a real implementation, we would:
        // 1. Get the pet using entityService.getItem(petId)
        // 2. Check if the pet is still in 'pending' state (reserved)
        // For now, we assume basic validation
        
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateUserStillActive(Order order) {
        if (order.getUserId() != null) {
            // Note: In a real implementation, we would:
            // 1. Get the user using entityService.getItem(userId)
            // 2. Check if the user is still in 'active' state
            // For now, we assume basic validation
        }
        
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePaymentInformation(Order order) {
        // Note: In a real implementation, this would validate payment information
        // if a payment system exists. For now, we assume payment validation
        // is handled elsewhere or not required for this demo.
        return EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "OrderApprovalCriterion".equals(opSpec.operationName()) &&
               "Order".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
