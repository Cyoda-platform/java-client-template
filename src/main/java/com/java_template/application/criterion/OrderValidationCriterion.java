package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationResponse;
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
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntity(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::checkOrderValidation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && order.getOrderId() != null;
    }

    private boolean checkOrderValidation(CriterionSerializer.CriterionEntityResponseExecutionContext<Order> context) {
        Order order = context.entity();
        
        logger.debug("Validating order: {}", order.getOrderId());

        try {
            // Check mandatory fields
            if (!validateMandatoryFields(order)) {
                logger.warn("Order validation failed - missing mandatory fields: {}", order.getOrderId());
                return false;
            }

            // Check line items
            if (!validateLineItems(order)) {
                logger.warn("Order validation failed - invalid line items: {}", order.getOrderId());
                return false;
            }

            // Check customer information
            if (!validateCustomerInformation(order)) {
                logger.warn("Order validation failed - invalid customer information: {}", order.getOrderId());
                return false;
            }

            // Check order totals
            if (!validateOrderTotals(order)) {
                logger.warn("Order validation failed - invalid order totals: {}", order.getOrderId());
                return false;
            }

            logger.info("Order validation passed: {}", order.getOrderId());
            return true;

        } catch (Exception e) {
            logger.error("Error during order validation for order: {}", order.getOrderId(), e);
            return false;
        }
    }

    private boolean validateMandatoryFields(Order order) {
        // Check order ID
        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            logger.debug("Order ID is missing");
            return false;
        }

        // Check channel
        if (order.getChannel() == null || order.getChannel().trim().isEmpty()) {
            logger.debug("Channel is missing");
            return false;
        }

        // Validate channel values
        if (!isValidChannel(order.getChannel())) {
            logger.debug("Invalid channel: {}", order.getChannel());
            return false;
        }

        // Check external reference for marketplace orders
        if ("marketplace".equals(order.getChannel()) && 
            (order.getExternalRef() == null || order.getExternalRef().trim().isEmpty())) {
            logger.debug("External reference is required for marketplace orders");
            return false;
        }

        return true;
    }

    private boolean isValidChannel(String channel) {
        return "web".equals(channel) || "store".equals(channel) || "marketplace".equals(channel);
    }

    private boolean validateLineItems(Order order) {
        // Check if line items exist
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            logger.debug("No line items found");
            return false;
        }

        // Validate each line item
        for (Order.LineItem lineItem : order.getLineItems()) {
            if (!validateLineItem(lineItem)) {
                return false;
            }
        }

        return true;
    }

    private boolean validateLineItem(Order.LineItem lineItem) {
        // Check product ID
        if (lineItem.getProductId() == null || lineItem.getProductId().trim().isEmpty()) {
            logger.debug("Line item missing product ID");
            return false;
        }

        // Check quantity
        if (lineItem.getQuantity() == null || lineItem.getQuantity() <= 0) {
            logger.debug("Line item has invalid quantity: {}", lineItem.getQuantity());
            return false;
        }

        // Check unit price
        if (lineItem.getUnitPrice() == null || lineItem.getUnitPrice() < 0) {
            logger.debug("Line item has invalid unit price: {}", lineItem.getUnitPrice());
            return false;
        }

        // Check description
        if (lineItem.getDescription() == null || lineItem.getDescription().trim().isEmpty()) {
            logger.debug("Line item missing description");
            return false;
        }

        return true;
    }

    private boolean validateCustomerInformation(Order order) {
        // Check if customer exists
        if (order.getCustomer() == null) {
            logger.debug("Customer information is missing");
            return false;
        }

        Order.Customer customer = order.getCustomer();

        // Check customer ID
        if (customer.getCustomerId() == null || customer.getCustomerId().trim().isEmpty()) {
            logger.debug("Customer ID is missing");
            return false;
        }

        // Check customer name
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            logger.debug("Customer name is missing");
            return false;
        }

        // Check addresses
        if (customer.getAddresses() == null || customer.getAddresses().isEmpty()) {
            logger.debug("Customer addresses are missing");
            return false;
        }

        // Validate at least one address
        boolean hasValidAddress = customer.getAddresses().stream()
                .anyMatch(this::validateAddress);

        if (!hasValidAddress) {
            logger.debug("No valid customer address found");
            return false;
        }

        return true;
    }

    private boolean validateAddress(Order.Address address) {
        return address.getLine1() != null && !address.getLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostcode() != null && !address.getPostcode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }

    private boolean validateOrderTotals(Order order) {
        // Check if total amount is set
        if (order.getTotalAmount() == null || order.getTotalAmount() < 0) {
            logger.debug("Invalid total amount: {}", order.getTotalAmount());
            return false;
        }

        // Calculate expected total from line items
        double calculatedTotal = order.getLineItems().stream()
                .mapToDouble(this::calculateLineItemTotal)
                .sum();

        // Allow small rounding differences (within 0.01)
        double difference = Math.abs(order.getTotalAmount() - calculatedTotal);
        if (difference > 0.01) {
            logger.debug("Order total mismatch - Expected: {}, Actual: {}", calculatedTotal, order.getTotalAmount());
            return false;
        }

        return true;
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
