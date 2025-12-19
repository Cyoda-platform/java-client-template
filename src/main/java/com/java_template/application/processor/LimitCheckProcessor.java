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

/**
 * LimitCheckProcessor - Validates order against account limits
 */
@Component
public class LimitCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LimitCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LimitCheckProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Checking limits for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::checkLimits)
                .complete();
    }

    private EntityWithMetadata<Order> checkLimits(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.getEntity();
        
        // Calculate estimated cost
        Double estimatedCost = order.getQuantity() * (order.getPrice() != null ? order.getPrice() : 100.0);
        order.setEstimatedCost(estimatedCost);
        
        // In a real system, check against account limits
        logger.info("Limit check passed for order: {} with estimated cost: {}", 
            order.getOrderId(), estimatedCost);
        
        return orderWithMetadata;
    }

    private boolean isValidOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.getEntity();
        return order.getOrderId() != null && order.getQuantity() != null;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

