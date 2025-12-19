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
 * OrderSettlementProcessor - Handles order settlement after execution
 */
@Component
public class OrderSettlementProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderSettlementProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderSettlementProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Settling Order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::settleOrder)
                .complete();
    }

    private EntityWithMetadata<Order> settleOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.getEntity();
        
        // Update order settlement status
        order.setUpdatedAt(LocalDateTime.now());
        order.setExecutedAt(LocalDateTime.now());
        
        logger.info("Order settled: {} with {} shares at avg price {}", 
            order.getOrderId(), order.getFilledQuantity(), order.getAveragePrice());
        
        return orderWithMetadata;
    }

    private boolean isValidOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.getEntity();
        return order.getOrderId() != null && 
               order.getFilledQuantity() != null && 
               order.getFilledQuantity() > 0;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

