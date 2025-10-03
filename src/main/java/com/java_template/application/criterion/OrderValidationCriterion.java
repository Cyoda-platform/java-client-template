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
 * ABOUTME: OrderValidationCriterion validates order completeness and business rules
 * before allowing transition from Draft to Submitted state.
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
            .evaluateEntity(Order.class, this::validateOrder)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrder(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        // Check if order is null (structural validation)
        if (order == null) {
            return EvaluationOutcome.fail("Order entity is null",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate basic order fields
        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order ID is required",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (order.getExternalRef() == null || order.getExternalRef().trim().isEmpty()) {
            return EvaluationOutcome.fail("External reference is required",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (order.getChannel() == null || order.getChannel().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order channel is required",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate channel is one of allowed values
        if (!isValidChannel(order.getChannel())) {
            return EvaluationOutcome.fail("Invalid channel: " + order.getChannel() + ". Must be web, store, or marketplace",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate customer information
        if (order.getCustomer() == null) {
            return EvaluationOutcome.fail("Customer information is required",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!order.getCustomer().isValid()) {
            return EvaluationOutcome.fail("Customer information is incomplete",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate line items
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            return EvaluationOutcome.fail("Order must have at least one line item",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate each line item
        for (int i = 0; i < order.getLineItems().size(); i++) {
            Order.LineItem lineItem = order.getLineItems().get(i);
            if (!lineItem.isValid()) {
                return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                            "Line item " + (i + 1) + " is invalid");
            }

            // Validate line item has positive quantity and price
            if (lineItem.getQuantity() <= 0) {
                return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                            "Line item " + (i + 1) + " must have positive quantity");
            }

            if (lineItem.getUnitPrice() < 0) {
                return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                            "Line item " + (i + 1) + " cannot have negative unit price");
            }
        }

        // Validate order total calculation
        Double calculatedTotal = order.getOrderTotal();
        if (calculatedTotal == null || calculatedTotal < 0) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.DATA_QUALITY_FAILURE, 
                                        "Order total calculation is invalid");
        }

        // Validate order total is reasonable (not zero for non-free orders)
        if (calculatedTotal == 0 && !isFreeOrder(order)) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                        "Order total cannot be zero unless it's a promotional order");
        }

        logger.debug("Order validation passed for orderId: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }

    private boolean isValidChannel(String channel) {
        return "web".equals(channel) || "store".equals(channel) || "marketplace".equals(channel);
    }

    private boolean isFreeOrder(Order order) {
        // Check if this is a promotional/free order
        // This could be determined by checking for promotional codes, 
        // special customer types, or other business rules
        return order.getLineItems().stream()
                .allMatch(item -> item.getUnitPrice() == 0.0);
    }
}
