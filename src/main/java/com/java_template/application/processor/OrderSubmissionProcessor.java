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
 * OrderSubmissionProcessor - Submits validated order to execution gateway
 */
@Component
public class OrderSubmissionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderSubmissionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderSubmissionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Submitting Order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(ctx -> submitOrder(ctx.entityResponse()))
                .complete();
    }

    private EntityWithMetadata<Order> submitOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.entity();

        // In a real system, this would call the execution gateway
        // For now, we simulate submission
        logger.info("Submitting order {} to execution gateway", order.getOrderId());

        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order submitted: {} for symbol: {}", order.getOrderId(), order.getSymbol());
        return orderWithMetadata;
    }

    private boolean isValidOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.entity();
        return order.getOrderId() != null &&
               order.getStatus() != null &&
               "NEW".equals(order.getStatus());
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

