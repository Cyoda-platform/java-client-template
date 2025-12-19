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
 * OrderCreationProcessor - Handles order creation and initialization
 */
@Component
public class OrderCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::processOrderCreation)
                .complete();
    }

    private EntityWithMetadata<Order> processOrderCreation(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.entity();

        // Initialize order state
        order.setStatus("NEW");
        order.setFilledQuantity(0.0);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order created: {} for account: {}", order.getOrderId(), order.getAccountId());
        return orderWithMetadata;
    }

    private boolean isValidOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.entity();
        return order.getOrderId() != null && !order.getOrderId().isBlank() &&
               order.getAccountId() != null && !order.getAccountId().isBlank() &&
               order.getSymbol() != null && !order.getSymbol().isBlank() &&
               order.getQuantity() != null && order.getQuantity() > 0;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

