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

import java.time.LocalDateTime;

/**
 * Processor for shipping orders.
 * Handles the ship_order transition from approved to shipped.
 */
@Component
public class OrderShippingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderShippingProcessor.class);
    private final ProcessorSerializer serializer;

    public OrderShippingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing order shipping for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .map(processingContext -> {
                Order order = processingContext.entity();
                
                // Set ship date to current timestamp
                order.setShipDate(LocalDateTime.now());
                
                // In a real implementation, this would generate a shipping tracking number
                // and send shipping notifications
                
                logger.info("Shipped order with ID: {} for pet ID: {}", order.getId(), order.getPetId());
                
                return order;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "OrderShippingProcessor".equals(opSpec.operationName()) &&
               "Order".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
