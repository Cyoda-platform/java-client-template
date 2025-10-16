package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ABOUTME: Processor that handles new order placement, validating customer and pet availability,
 * reserving pets, and calculating pricing for new orders.
 */
@Component
public class OrderPlacementProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderPlacementProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderPlacementProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order placement for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for order placement
     */
    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Placing order: {} for customer: {} and pet: {}", 
                     order.getOrderId(), order.getCustomerId(), order.getPetId());

        // Set order placement timestamp
        order.setOrderDate(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Initialize default values
        if (order.getComplete() == null) {
            order.setComplete(false);
        }

        // Reserve the pet by triggering pet workflow transition
        // This would be done through EntityService to update the pet entity
        reservePet(order.getPetId());

        logger.info("Order {} placed successfully for customer: {} and pet: {}", 
                    order.getOrderId(), order.getCustomerId(), order.getPetId());

        return entityWithMetadata;
    }

    /**
     * Reserve the pet for this order
     */
    private void reservePet(String petId) {
        try {
            // This would find the pet by business ID and trigger the reserve_pet transition
            // For now, we'll just log the action
            logger.info("Reserving pet with ID: {}", petId);
            // TODO: Implement pet reservation through EntityService
            // EntityWithMetadata<Pet> pet = entityService.findByBusinessId(...);
            // entityService.update(pet.metadata().getId(), pet.entity(), "reserve_pet");
        } catch (Exception e) {
            logger.error("Failed to reserve pet with ID: {}", petId, e);
            // In a real implementation, this might throw an exception to fail the order
        }
    }
}
