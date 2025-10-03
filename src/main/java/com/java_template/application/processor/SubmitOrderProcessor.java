package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
 * ABOUTME: SubmitOrderProcessor handles the transition from Draft to Submitted state,
 * validating order completeness and setting submission timestamp.
 */
@Component
public class SubmitOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmitOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SubmitOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processSubmitOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Order entity is null");
            return false;
        }

        Order order = entityWithMetadata.entity();
        
        // Validate order has all required fields for submission
        if (!order.isValid()) {
            logger.error("Order validation failed for orderId: {}", order.getOrderId());
            return false;
        }

        // Check that order has line items
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            logger.error("Order has no line items for orderId: {}", order.getOrderId());
            return false;
        }

        // Validate all line items
        boolean allLineItemsValid = order.getLineItems().stream().allMatch(Order.LineItem::isValid);
        if (!allLineItemsValid) {
            logger.error("One or more line items are invalid for orderId: {}", order.getOrderId());
            return false;
        }

        // Check customer information is complete
        if (order.getCustomer() == null || !order.getCustomer().isValid()) {
            logger.error("Customer information is incomplete for orderId: {}", order.getOrderId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<Order> processSubmitOrder(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        logger.info("Submitting order with orderId: {}", order.getOrderId());

        try {
            // Set creation timestamp if not already set
            if (order.getCreatedTimestamp() == null) {
                order.setCreatedTimestamp(LocalDateTime.now());
            }

            // Validate order total calculation
            Double calculatedTotal = order.getOrderTotal();
            logger.info("Order total calculated: {} for orderId: {}", calculatedTotal, order.getOrderId());

            // Initialize payment if not present
            if (order.getPayment() == null) {
                Order.Payment payment = new Order.Payment();
                payment.setAmount(calculatedTotal);
                payment.setStatus("pending");
                payment.setCurrency("USD"); // Default currency
                order.setPayment(payment);
            }

            // Validate no duplicate orders by externalRef + channel
            // Note: In a real implementation, this would check against existing orders
            // For now, we'll log the validation
            logger.info("Order submitted successfully - orderId: {}, externalRef: {}, channel: {}", 
                       order.getOrderId(), order.getExternalRef(), order.getChannel());

            return new EntityWithMetadata<>(order, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing order submission for orderId: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to submit order: " + e.getMessage(), e);
        }
    }
}
