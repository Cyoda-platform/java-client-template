package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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

@Component
public class OrderProcessingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderProcessingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order state for processing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        if (order == null) {
            logger.error("Order entity is null");
            return false;
        }
        if (!"pending".equalsIgnoreCase(order.getStatus())) {
            logger.error("Order status is not pending: {}", order.getStatus());
            return false;
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            logger.error("Order has no items");
            return false;
        }
        return true;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        // Process order:
        // 1. Check stock availability for each OrderItem
        // 2. If any stock insufficient, mark order as FAILED
        // 3. If stock sufficient, deduct stock quantities
        // 4. Update Cart status to CHECKED_OUT (assumed separate call or event)
        // 5. Update order status to COMPLETED

        // TODO: Implement stock validation and deduction with external stock service

        order.setStatus("completed");
        logger.info("Order {} processed successfully", order.getOrderId());

        return order;
    }
}
