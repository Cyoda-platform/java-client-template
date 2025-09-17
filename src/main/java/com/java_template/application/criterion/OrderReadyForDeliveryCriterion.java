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
 * OrderReadyForDeliveryCriterion - Checks if an approved order is ready for delivery
 * 
 * Transition: deliver_order
 * Purpose: Validates order delivery readiness and shipping information
 */
@Component
public class OrderReadyForDeliveryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderReadyForDeliveryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order delivery readiness criteria for request: {}", request.getId());
        
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
     * Main validation logic for order delivery readiness
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
            return EvaluationOutcome.fail("Order is not in approved state for delivery", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check order validity
        if (!order.isValid()) {
            logger.warn("Order {} is not valid", order.getOrderId());
            return EvaluationOutcome.fail("Order data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate shipping address is present
        if (order.getShippingAddress() == null) {
            logger.warn("Order {} has no shipping address", order.getOrderId());
            return EvaluationOutcome.fail("Shipping address is required for delivery", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        Order.Address shippingAddress = order.getShippingAddress();
        if (shippingAddress.getLine1() == null || shippingAddress.getLine1().trim().isEmpty()) {
            logger.warn("Order {} has incomplete shipping address", order.getOrderId());
            return EvaluationOutcome.fail("Complete shipping address is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (shippingAddress.getCity() == null || shippingAddress.getCity().trim().isEmpty()) {
            logger.warn("Order {} has no city in shipping address", order.getOrderId());
            return EvaluationOutcome.fail("City is required in shipping address", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (shippingAddress.getPostcode() == null || shippingAddress.getPostcode().trim().isEmpty()) {
            logger.warn("Order {} has no postcode in shipping address", order.getOrderId());
            return EvaluationOutcome.fail("Postcode is required in shipping address", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate customer contact info
        if (order.getCustomerInfo() == null) {
            logger.warn("Order {} has no customer information", order.getOrderId());
            return EvaluationOutcome.fail("Customer information is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        Order.CustomerInfo customerInfo = order.getCustomerInfo();
        if (customerInfo.getEmail() == null || customerInfo.getEmail().trim().isEmpty()) {
            logger.warn("Order {} has no customer email", order.getOrderId());
            return EvaluationOutcome.fail("Customer email is required for delivery notifications", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate payment has been processed (check total amount)
        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            logger.warn("Order {} has invalid payment amount", order.getOrderId());
            return EvaluationOutcome.fail("Valid payment amount is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Order {} is ready for delivery", order.getOrderId());
        return EvaluationOutcome.success();
    }
}
