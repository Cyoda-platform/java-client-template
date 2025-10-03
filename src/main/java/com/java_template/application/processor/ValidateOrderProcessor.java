package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Processor to validate order before submission
 * Validates mandatory fields, line items, and calculates totals
 */
@Component
public class ValidateOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processOrderValidation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processOrderValidation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Validating order: {}", order.getOrderId());

        // Validate mandatory fields
        validateMandatoryFields(order);

        // Validate and calculate line item totals
        calculateLineItemTotals(order);

        // Calculate order total
        calculateOrderTotal(order);

        // Validate customer information
        validateCustomerInformation(order);

        // Set validation timestamp
        order.setUpdatedTimestamp(LocalDateTime.now());
        order.setLastUpdatedBy("ValidateOrderProcessor");

        logger.info("Order {} validated successfully with total: {}", 
                   order.getOrderId(), order.getTotalAmount());

        return entityWithMetadata;
    }

    private void validateMandatoryFields(Order order) {
        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID is required");
        }
        
        if (order.getChannel() == null || order.getChannel().trim().isEmpty()) {
            throw new IllegalArgumentException("Channel is required");
        }
        
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line item");
        }
        
        if (order.getCustomer() == null) {
            throw new IllegalArgumentException("Customer information is required");
        }
    }

    private void calculateLineItemTotals(Order order) {
        for (Order.LineItem lineItem : order.getLineItems()) {
            if (lineItem.getQuantity() == null || lineItem.getQuantity() <= 0) {
                throw new IllegalArgumentException("Line item quantity must be positive");
            }
            
            if (lineItem.getUnitPrice() == null || lineItem.getUnitPrice() < 0) {
                throw new IllegalArgumentException("Line item unit price must be non-negative");
            }
            
            // Calculate line total: (quantity * unitPrice) - discount + tax
            double lineTotal = lineItem.getQuantity() * lineItem.getUnitPrice();
            
            if (lineItem.getDiscount() != null) {
                lineTotal -= lineItem.getDiscount();
            }
            
            if (lineItem.getTax() != null) {
                lineTotal += lineItem.getTax();
            }
            
            lineItem.setLineTotal(lineTotal);
            
            // Set initial fulfillment status if not set
            if (lineItem.getFulfilmentStatus() == null) {
                lineItem.setFulfilmentStatus("pending");
            }
        }
    }

    private void calculateOrderTotal(Order order) {
        double total = order.getLineItems().stream()
                .mapToDouble(lineItem -> lineItem.getLineTotal() != null ? lineItem.getLineTotal() : 0.0)
                .sum();
        
        order.setTotalAmount(total);
        
        // Set default currency if not provided
        if (order.getCurrency() == null) {
            order.setCurrency("USD");
        }
    }

    private void validateCustomerInformation(Order order) {
        Order.Customer customer = order.getCustomer();
        
        if (customer.getCustomerId() == null || customer.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        
        // Validate at least one address exists
        if (customer.getAddresses() == null || customer.getAddresses().isEmpty()) {
            throw new IllegalArgumentException("Customer must have at least one address");
        }
        
        // Ensure at least one address is marked as default
        boolean hasDefaultAddress = customer.getAddresses().stream()
                .anyMatch(Order.Address::isDefault);
        
        if (!hasDefaultAddress) {
            // Mark first address as default
            customer.getAddresses().get(0).setDefault(true);
        }
    }
}
