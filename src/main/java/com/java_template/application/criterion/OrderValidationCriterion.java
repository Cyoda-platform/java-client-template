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
 * OrderValidationCriterion - Validates that an order can be approved
 * 
 * Transition: approve_order
 * Purpose: Validates order data and customer information
 */
@Component
public class OrderValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order validation criteria for request: {}", request.getId());
        
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
     * Main validation logic for order approval
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify order is in placed state
        if (!"placed".equals(currentState)) {
            logger.warn("Order {} is not in placed state, current state: {}", order.getOrderId(), currentState);
            return EvaluationOutcome.fail("Order is not in placed state for approval", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check order validity
        if (!order.isValid()) {
            logger.warn("Order {} is not valid", order.getOrderId());
            return EvaluationOutcome.fail("Order data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate customer information
        if (order.getCustomerInfo() == null) {
            logger.warn("Order {} has no customer information", order.getOrderId());
            return EvaluationOutcome.fail("Customer information is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        Order.CustomerInfo customerInfo = order.getCustomerInfo();
        if (customerInfo.getCustomerId() == null || customerInfo.getCustomerId().trim().isEmpty()) {
            logger.warn("Order {} has invalid customer ID", order.getOrderId());
            return EvaluationOutcome.fail("Valid customer ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (customerInfo.getEmail() == null || customerInfo.getEmail().trim().isEmpty() || !customerInfo.getEmail().contains("@")) {
            logger.warn("Order {} has invalid customer email", order.getOrderId());
            return EvaluationOutcome.fail("Valid customer email is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate order total
        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            logger.warn("Order {} has invalid total amount: {}", order.getOrderId(), order.getTotalAmount());
            return EvaluationOutcome.fail("Valid order total amount is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate pet ID
        if (order.getPetId() == null || order.getPetId().trim().isEmpty()) {
            logger.warn("Order {} has no pet ID", order.getOrderId());
            return EvaluationOutcome.fail("Pet ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate quantity
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            logger.warn("Order {} has invalid quantity: {}", order.getOrderId(), order.getQuantity());
            return EvaluationOutcome.fail("Valid quantity is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        logger.debug("Order {} is valid for approval", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
