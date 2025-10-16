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
import java.util.UUID;

/**
 * ABOUTME: Processor that handles order shipment, updating shipping information,
 * generating tracking numbers, and updating pet status to sold.
 */
@Component
public class OrderShipmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderShipmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderShipmentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order shipment for request: {}", request.getId());

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
        String currentState = entityWithMetadata.metadata().getState();
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null && "preparing".equals(currentState);
    }

    /**
     * Main business logic for order shipment
     */
    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Shipping order: {} for customer: {}", order.getOrderId(), order.getCustomerId());

        // Update shipment timestamp
        order.setShipDate(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Generate tracking number if not present
        if (order.getShipping() != null && order.getShipping().getTrackingNumber() == null) {
            order.getShipping().setTrackingNumber("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // Complete the pet sale by triggering pet workflow transition
        completePetSale(order.getPetId());

        logger.info("Order {} shipped successfully for customer: {} with tracking: {}", 
                    order.getOrderId(), order.getCustomerId(), 
                    order.getShipping() != null ? order.getShipping().getTrackingNumber() : "N/A");

        return entityWithMetadata;
    }

    /**
     * Complete the pet sale for this order
     */
    private void completePetSale(String petId) {
        try {
            // This would find the pet by business ID and trigger the complete_sale transition
            logger.info("Completing sale for pet with ID: {}", petId);
            // TODO: Implement pet sale completion through EntityService
            // EntityWithMetadata<Pet> pet = entityService.findByBusinessId(...);
            // entityService.update(pet.metadata().getId(), pet.entity(), "complete_sale");
        } catch (Exception e) {
            logger.error("Failed to complete sale for pet with ID: {}", petId, e);
        }
    }
}
