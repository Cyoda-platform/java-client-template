package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
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
import java.util.UUID;

/**
 * Processor for creating new orders in the system.
 * Handles the place_order transition from initial_state to placed.
 */
@Component
public class OrderCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreationProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderCreationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing order creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(order -> order.getPetId() != null, "Pet ID is required")
            .validate(order -> order.getQuantity() != null && order.getQuantity() > 0, 
                     "Quantity must be positive")
            .map(context -> {
                Order order = context.entity();
                
                // Generate unique order ID if not provided
                if (order.getId() == null) {
                    order.setId(System.currentTimeMillis()); // Simple ID generation
                }
                
                // Set default quantity to 1 if not provided
                if (order.getQuantity() == null) {
                    order.setQuantity(1);
                }
                
                // Set complete flag to false
                order.setComplete(false);
                
                // Set order timestamp if ship date not provided
                if (order.getShipDate() == null) {
                    order.setShipDate(LocalDateTime.now().plusDays(1)); // Default to tomorrow
                }
                
                logger.info("Created order with ID: {} for pet ID: {}", order.getId(), order.getPetId());
                
                // Note: In a real implementation, we would trigger pet reservation here
                // by calling entityService.applyTransition() on the pet entity
                // However, per the requirements, we focus on the current entity processing
                
                return order;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "OrderCreationProcessor".equals(opSpec.operationName()) &&
               "Order".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
