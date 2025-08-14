package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProcessOrder implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessOrder.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessOrder(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid Order entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        if (order == null) return false;
        if (order.getStatus() == null || order.getItems() == null) return false;
        return true;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        logger.info("Start processing Order with ID: {}", order.getOrderId());

        // Business logic:
        // 1. Check stock availability for each OrderItem
        // 2. Deduct stock from inventory
        // 3. Update related Cart status to CHECKED_OUT
        // 4. Update Order status accordingly

        // For demonstration, we simulate stock check and deduction
        boolean stockSufficient = checkStockForOrder(order);
        if (stockSufficient) {
            // Deduct stock - in real implementation, call external stock service
            deductStock(order);
            // Update Order status to COMPLETED
            order.setStatus("COMPLETED");
            // Update Cart status to CHECKED_OUT
            updateCartStatus(order.getCustomerId(), "CHECKED_OUT");
            logger.info("Order {} processed successfully.", order.getOrderId());
        } else {
            // Stock not sufficient - mark order as FAILED
            order.setStatus("FAILED");
            logger.warn("Order {} processing failed due to insufficient stock.", order.getOrderId());
        }

        return order;
    }

    private boolean checkStockForOrder(Order order) {
        // TODO: Implement real stock availability check
        // For now, assume stock is always sufficient
        return true;
    }

    private void deductStock(Order order) {
        // TODO: Implement stock deduction logic
        // This is a placeholder for integration with inventory management
        logger.info("Deducting stock for Order ID: {}", order.getOrderId());
    }

    private void updateCartStatus(String customerId, String status) {
        // TODO: Implement cart status update logic
        // This would typically involve fetching the Cart by customerId and updating status
        logger.info("Updating Cart status to {} for customerId: {}", status, customerId);
    }
}
