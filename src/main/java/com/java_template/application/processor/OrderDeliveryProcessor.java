package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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

/**
 * Processor for delivering orders.
 * Handles the deliver_order transition from shipped to delivered.
 */
@Component
public class OrderDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderDeliveryProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderDeliveryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing order delivery for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .map(context -> {
                Order order = context.entity();
                
                // Set complete flag to true
                order.setComplete(true);
                
                logger.info("Delivered order with ID: {} for pet ID: {}", order.getId(), order.getPetId());
                
                // Note: In a real implementation, this would trigger pet sale completion
                // by calling entityService.applyTransition() on the pet entity with "sell_pet"
                // and send delivery confirmation notifications
                
                return order;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "OrderDeliveryProcessor".equals(opSpec.operationName()) &&
               "Order".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
