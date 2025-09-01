package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OrderStartPickingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderStartPickingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public OrderStartPickingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order start picking for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Starting picking process for order: {}", order.getOrderId());

        // Validate order entity
        if (order == null) {
            logger.error("Order entity is null");
            throw new IllegalArgumentException("Order entity cannot be null");
        }

        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            logger.error("Order ID is required");
            throw new IllegalArgumentException("Order ID is required");
        }

        // Find associated shipment by order ID
        updateShipmentState(order.getOrderId(), "READY_FOR_SHIPPING");

        // Update order timestamp
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order picking started successfully for order: {}", order.getOrderId());

        return order;
    }

    /**
     * Update the associated shipment state to PICKING.
     */
    private void updateShipmentState(String orderId, String transition) {
        try {
            logger.info("Updating shipment state for order: {} with transition: {}", orderId, transition);

            // Find shipment by order ID
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("orderId", "equals", orderId));
            
            var shipmentResponse = entityService.getFirstItemByCondition(Shipment.class, condition, false);
            
            if (shipmentResponse.isPresent()) {
                Shipment shipment = shipmentResponse.get().getData();
                UUID entityId = shipmentResponse.get().getMetadata().getId();
                
                // Update shipment timestamp
                shipment.setUpdatedAt(LocalDateTime.now());
                
                // Update shipment with transition (this will move it to PICKING state)
                entityService.update(entityId, shipment, transition);
                
                logger.info("Shipment state updated successfully: {} for order: {}", 
                           shipment.getShipmentId(), orderId);
                
            } else {
                logger.error("Shipment not found for order: {}", orderId);
                throw new IllegalStateException("Shipment not found for order: " + orderId);
            }
            
        } catch (Exception e) {
            logger.error("Error updating shipment state for order: {}", orderId, e);
            throw new RuntimeException("Failed to update shipment state", e);
        }
    }
}
