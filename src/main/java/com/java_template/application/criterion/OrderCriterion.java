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

import java.util.Arrays;
import java.util.List;

/**
 * OrderCriterion - Validates order business rules
 * 
 * This criterion validates:
 * - Required fields are present and valid
 * - Order status is valid
 * - Order lines are valid
 * - Totals are correctly calculated
 * - Guest contact information is complete and valid
 * - Order number format is correct
 */
@Component
public class OrderCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final List<String> VALID_ORDER_STATUSES = Arrays.asList(
        "WAITING_TO_FULFILL", "PICKING", "WAITING_TO_SEND", "SENT", "DELIVERED"
    );

    public OrderCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrder)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the Order entity
     */
    private EvaluationOutcome validateOrder(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        // Check if order is null (structural validation)
        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Basic entity validation
        if (!order.isValid()) {
            logger.warn("Order basic validation failed for orderId: {}", order.getOrderId());
            return EvaluationOutcome.fail("Order basic validation failed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate order status
        if (!VALID_ORDER_STATUSES.contains(order.getStatus())) {
            logger.warn("Invalid order status: {}", order.getStatus());
            return EvaluationOutcome.fail("Invalid order status: " + order.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate order lines
        if (order.getLines() == null || order.getLines().isEmpty()) {
            logger.warn("Order must have at least one line item");
            return EvaluationOutcome.fail("Order must have at least one line item", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        for (Order.OrderLine line : order.getLines()) {
            EvaluationOutcome lineValidation = validateOrderLine(line);
            if (!lineValidation.isSuccess()) {
                return lineValidation;
            }
        }

        // Validate totals
        EvaluationOutcome totalsValidation = validateTotals(order);
        if (!totalsValidation.isSuccess()) {
            return totalsValidation;
        }

        // Validate guest contact (required for orders)
        EvaluationOutcome contactValidation = validateGuestContact(order.getGuestContact());
        if (!contactValidation.isSuccess()) {
            return contactValidation;
        }

        // Validate order number format (should be short ULID)
        if (order.getOrderNumber() != null && (order.getOrderNumber().length() < 5 || order.getOrderNumber().length() > 20)) {
            logger.warn("Order number format invalid: {}", order.getOrderNumber());
            return EvaluationOutcome.fail("Order number must be between 5 and 20 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates individual order line
     */
    private EvaluationOutcome validateOrderLine(Order.OrderLine line) {
        if (line == null) {
            return EvaluationOutcome.fail("Order line is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (line.getSku() == null || line.getSku().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order line SKU is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getName() == null || line.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Order line name is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getUnitPrice() == null || line.getUnitPrice() < 0) {
            return EvaluationOutcome.fail("Order line unit price must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getQty() == null || line.getQty() <= 0) {
            return EvaluationOutcome.fail("Order line quantity must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate line total calculation
        if (line.getLineTotal() != null) {
            double expectedTotal = line.getUnitPrice() * line.getQty();
            if (Math.abs(line.getLineTotal() - expectedTotal) > 0.01) {
                return EvaluationOutcome.fail("Order line total calculation is incorrect", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates order totals consistency
     */
    private EvaluationOutcome validateTotals(Order order) {
        if (order.getTotals() == null) {
            return EvaluationOutcome.fail("Order totals are required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Calculate expected totals
        double expectedItemsTotal = 0.0;

        for (Order.OrderLine line : order.getLines()) {
            if (line.getLineTotal() != null) {
                expectedItemsTotal += line.getLineTotal();
            }
        }

        // Validate items total
        if (order.getTotals().getItems() == null || Math.abs(order.getTotals().getItems() - expectedItemsTotal) > 0.01) {
            return EvaluationOutcome.fail("Order items total calculation is incorrect", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate grand total (should equal items total for demo)
        if (order.getTotals().getGrand() == null || Math.abs(order.getTotals().getGrand() - order.getTotals().getItems()) > 0.01) {
            return EvaluationOutcome.fail("Order grand total should equal items total", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates guest contact information (required for orders)
     */
    private EvaluationOutcome validateGuestContact(Order.GuestContact guestContact) {
        if (guestContact == null) {
            return EvaluationOutcome.fail("Guest contact is required for orders", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (guestContact.getName() == null || guestContact.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Guest contact name is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate address (required for orders)
        if (guestContact.getAddress() == null) {
            return EvaluationOutcome.fail("Guest contact address is required for orders", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Order.Address address = guestContact.getAddress();
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            return EvaluationOutcome.fail("Address line 1 is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            return EvaluationOutcome.fail("Address city is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            return EvaluationOutcome.fail("Address postcode is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            return EvaluationOutcome.fail("Address country is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
