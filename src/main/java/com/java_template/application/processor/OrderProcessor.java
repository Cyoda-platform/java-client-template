package com.java_template.application.processor;

import com.java_template.application.entity.Order;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order entity = context.entity();
        // The business logic corresponds to processOrder() flow:
        // 1. Validation: Verify product stock availability for each order item
        // 2. Processing: Deduct stock, calculate totals, save order snapshot
        // 3. Completion: Update order status to CONFIRMED or FAILED
        // 4. Notification: Send order confirmation to customer, notify inventory

        // Since detailed stock deduction and external calls are not available,
        // we implement the logic according to the entity fields and described flow.

        // Check if status is PENDING otherwise no processing
        if (!"PENDING".equalsIgnoreCase(entity.getStatus())) {
            logger.warn("Order not in PENDING state: {}", entity.getStatus());
            return entity;
        }

        // Simulate stock availability check (should be done externally, so assume passed here)
        // Calculate total amount from order items to verify consistency
        double calculatedTotal = 0.0;
        if (entity.getOrderItems() != null) {
            for (var item : entity.getOrderItems()) {
                if (item.getQuantity() == null || item.getPriceAtPurchase() == null) {
                    logger.error("Invalid order item quantity or price for order: {}", entity.getCustomerId());
                    entity.setStatus("FAILED");
                    return entity;
                }
                calculatedTotal += item.getQuantity() * item.getPriceAtPurchase();
            }
        }

        // Check if totalAmount matches calculated total (allow small epsilon)
        if (Math.abs(calculatedTotal - entity.getTotalAmount()) > 0.01) {
            logger.error("Total amount mismatch for order: {}. Expected: {}, Actual: {}", entity.getCustomerId(), calculatedTotal, entity.getTotalAmount());
            entity.setStatus("FAILED");
            return entity;
        }

        // Deduct stock and update status to CONFIRMED
        // Actual stock deduction not possible here, assume successful
        entity.setStatus("CONFIRMED");

        // Notification logic would be external, so just log here
        logger.info("Order confirmed for customerId: {}", entity.getCustomerId());

        return entity;
    }
}
