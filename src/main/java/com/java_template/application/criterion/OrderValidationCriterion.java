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
 * Criterion to validate order before submission
 * Checks mandatory fields, line items, and customer information
 */
@Component
public class OrderValidationCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public OrderValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

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

        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        logger.debug("Validating order: {}", order.getOrderId());

        try {
            // Check mandatory fields
            EvaluationOutcome mandatoryFieldsResult = validateMandatoryFields(order);
            if (!mandatoryFieldsResult.isSuccess()) {
                return mandatoryFieldsResult;
            }

            // Check line items
            EvaluationOutcome lineItemsResult = validateLineItems(order);
            if (!lineItemsResult.isSuccess()) {
                return lineItemsResult;
            }

            // Check customer information
            EvaluationOutcome customerResult = validateCustomerInformation(order);
            if (!customerResult.isSuccess()) {
                return customerResult;
            }

            // Check order totals
            EvaluationOutcome totalsResult = validateOrderTotals(order);
            if (!totalsResult.isSuccess()) {
                return totalsResult;
            }

            logger.info("Order validation passed: {}", order.getOrderId());
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error during order validation for order: {}", order.getOrderId(), e);
            return EvaluationOutcome.fail("Validation error: " + e.getMessage(),
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }

    private EvaluationOutcome validateMandatoryFields(Order order) {
        // Check order ID
        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            logger.debug("Order ID is missing");
            return EvaluationOutcome.fail("Order ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check channel
        if (order.getChannel() == null || order.getChannel().trim().isEmpty()) {
            logger.debug("Channel is missing");
            return EvaluationOutcome.fail("Channel is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate channel values
        if (!isValidChannel(order.getChannel())) {
            logger.debug("Invalid channel: {}", order.getChannel());
            return EvaluationOutcome.fail("Invalid channel: " + order.getChannel(),
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check external reference for marketplace orders
        if ("marketplace".equals(order.getChannel()) &&
            (order.getExternalRef() == null || order.getExternalRef().trim().isEmpty())) {
            logger.debug("External reference is required for marketplace orders");
            return EvaluationOutcome.fail("External reference is required for marketplace orders",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private boolean isValidChannel(String channel) {
        return "web".equals(channel) || "store".equals(channel) || "marketplace".equals(channel);
    }

    private EvaluationOutcome validateLineItems(Order order) {
        // Check if line items exist
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            logger.debug("No line items found");
            return EvaluationOutcome.fail("Order must have at least one line item",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate each line item
        for (Order.LineItem lineItem : order.getLineItems()) {
            EvaluationOutcome lineItemResult = validateLineItem(lineItem);
            if (!lineItemResult.isSuccess()) {
                return lineItemResult;
            }
        }

        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateLineItem(Order.LineItem lineItem) {
        // Check product ID
        if (lineItem.getProductId() == null || lineItem.getProductId().trim().isEmpty()) {
            logger.debug("Line item missing product ID");
            return EvaluationOutcome.fail("Line item missing product ID",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check quantity
        if (lineItem.getQuantity() == null || lineItem.getQuantity() <= 0) {
            logger.debug("Line item has invalid quantity: {}", lineItem.getQuantity());
            return EvaluationOutcome.fail("Line item quantity must be positive",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check unit price
        if (lineItem.getUnitPrice() == null || lineItem.getUnitPrice() < 0) {
            logger.debug("Line item has invalid unit price: {}", lineItem.getUnitPrice());
            return EvaluationOutcome.fail("Line item unit price cannot be negative",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check description
        if (lineItem.getDescription() == null || lineItem.getDescription().trim().isEmpty()) {
            logger.debug("Line item missing description");
            return EvaluationOutcome.fail("Line item description is required",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateCustomerInformation(Order order) {
        // Check if customer exists
        if (order.getCustomer() == null) {
            logger.debug("Customer information is missing");
            return EvaluationOutcome.fail("Customer information is required",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        Order.Customer customer = order.getCustomer();

        // Check customer ID
        if (customer.getCustomerId() == null || customer.getCustomerId().trim().isEmpty()) {
            logger.debug("Customer ID is missing");
            return EvaluationOutcome.fail("Customer ID is required",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check customer name
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            logger.debug("Customer name is missing");
            return EvaluationOutcome.fail("Customer name is required",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check addresses
        if (customer.getAddresses() == null || customer.getAddresses().isEmpty()) {
            logger.debug("Customer addresses are missing");
            return EvaluationOutcome.fail("Customer must have at least one address",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate at least one address
        boolean hasValidAddress = customer.getAddresses().stream()
                .anyMatch(this::validateAddress);

        if (!hasValidAddress) {
            logger.debug("No valid customer address found");
            return EvaluationOutcome.fail("Customer must have at least one valid address",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private boolean validateAddress(Order.Address address) {
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    private EvaluationOutcome validateOrderTotals(Order order) {
        // Check if total amount is set
        if (order.getTotalAmount() == null || order.getTotalAmount() < 0) {
            logger.debug("Invalid total amount: {}", order.getTotalAmount());
            return EvaluationOutcome.fail("Order total amount must be non-negative",
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Calculate expected total from line items
        double calculatedTotal = order.getLineItems().stream()
                .mapToDouble(this::calculateLineItemTotal)
                .sum();

        // Allow small rounding differences (within 0.01)
        double difference = Math.abs(order.getTotalAmount() - calculatedTotal);
        if (difference > 0.01) {
            logger.debug("Order total mismatch - Expected: {}, Actual: {}", calculatedTotal, order.getTotalAmount());
            return EvaluationOutcome.fail(String.format("Order total mismatch - Expected: %.2f, Actual: %.2f",
                                                       calculatedTotal, order.getTotalAmount()),
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private double calculateLineItemTotal(Order.LineItem lineItem) {
        double total = lineItem.getQuantity() * lineItem.getUnitPrice();
        
        if (lineItem.getDiscount() != null) {
            total -= lineItem.getDiscount();
        }
        
        if (lineItem.getTax() != null) {
            total += lineItem.getTax();
        }
        
        return total;
    }
}
