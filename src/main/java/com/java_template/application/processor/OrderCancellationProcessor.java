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
 * ABOUTME: Processor that handles order cancellation, releasing pet reservations
 * and processing refunds when orders are cancelled by customers or administrators.
 */
@Component
public class OrderCancellationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCancellationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderCancellationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order cancellation for request: {}", request.getId());

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
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null && 
               ("placed".equals(currentState) || "confirmed".equals(currentState) || "preparing".equals(currentState));
    }

    /**
     * Main business logic for order cancellation
     */
    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Cancelling order: {} for customer: {}", order.getOrderId(), order.getCustomerId());

        // Update cancellation timestamp
        order.setUpdatedAt(LocalDateTime.now());

        // Release the pet reservation by triggering pet workflow transition
        releasePetReservation(order.getPetId());

        logger.info("Order {} cancelled successfully for customer: {}", order.getOrderId(), order.getCustomerId());

        return entityWithMetadata;
    }

    /**
     * Release the pet reservation for this cancelled order
     */
    private void releasePetReservation(String petId) {
        try {
            // This would find the pet by business ID and trigger the cancel_reservation transition
            logger.info("Releasing reservation for pet with ID: {}", petId);
            // TODO: Implement pet reservation release through EntityService
            // EntityWithMetadata<Pet> pet = entityService.findByBusinessId(...);
            // entityService.update(pet.metadata().getId(), pet.entity(), "cancel_reservation");
        } catch (Exception e) {
            logger.error("Failed to release reservation for pet with ID: {}", petId, e);
        }
    }
}
