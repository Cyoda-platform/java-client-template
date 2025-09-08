package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ShipmentUpdateOrderStateProcessor - Updates order state based on shipment changes
 * 
 * This processor handles:
 * - Updating order status based on shipment status changes
 * - Synchronizing order and shipment states
 * - Setting updated timestamps
 * 
 * Triggered by: COMPLETE_PICKING, DISPATCH_SHIPMENT, CONFIRM_DELIVERY transitions
 * 
 * State mapping:
 * - Shipment PICKING → Order PICKING
 * - Shipment WAITING_TO_SEND → Order WAITING_TO_SEND  
 * - Shipment SENT → Order SENT
 * - Shipment DELIVERED → Order DELIVERED
 */
@Component
public class ShipmentUpdateOrderStateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentUpdateOrderStateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ShipmentUpdateOrderStateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing shipment state update for order synchronization, request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Shipment.class)
            .validate(this::isValidEntityWithMetadata, "Invalid shipment entity")
            .map(this::processShipmentOrderUpdateLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the Shipment EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Shipment> entityWithMetadata) {
        Shipment shipment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return shipment != null && shipment.isValid() && technicalId != null;
    }

    /**
     * Main business logic for updating order state based on shipment changes
     */
    private EntityWithMetadata<Shipment> processShipmentOrderUpdateLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Shipment> context) {
        
        EntityWithMetadata<Shipment> entityWithMetadata = context.entityResponse();
        Shipment shipment = entityWithMetadata.entity();

        logger.debug("Updating order state based on shipment: {} status: {}", 
                    shipment.getShipmentId(), shipment.getStatus());

        try {
            // Find the associated order
            Order order = findOrderByOrderId(shipment.getOrderId());
            if (order != null) {
                // Update order status based on shipment status
                String newOrderStatus = mapShipmentStatusToOrderStatus(shipment.getStatus());
                if (newOrderStatus != null && !newOrderStatus.equals(order.getStatus())) {
                    updateOrderStatus(order, newOrderStatus);
                }
            }

            // Update shipment timestamp
            shipment.setUpdatedAt(LocalDateTime.now());

        } catch (Exception e) {
            logger.error("Error updating order state for shipment: {}", shipment.getShipmentId(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Finds an order by its order ID
     */
    private Order findOrderByOrderId(String orderId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec()
                .withName(Order.ENTITY_NAME)
                .withVersion(Order.ENTITY_VERSION);

            SimpleCondition orderIdCondition = new SimpleCondition()
                .withJsonPath("$.orderId")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(orderId));

            GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(orderIdCondition));

            List<EntityWithMetadata<Order>> orders = entityService.search(
                orderModelSpec, condition, Order.class);

            if (!orders.isEmpty()) {
                return orders.get(0).entity();
            }
        } catch (Exception e) {
            logger.error("Error finding order by ID: {}", orderId, e);
        }
        return null;
    }

    /**
     * Maps shipment status to corresponding order status
     */
    private String mapShipmentStatusToOrderStatus(String shipmentStatus) {
        switch (shipmentStatus) {
            case "PICKING":
                return "PICKING";
            case "WAITING_TO_SEND":
                return "WAITING_TO_SEND";
            case "SENT":
                return "SENT";
            case "DELIVERED":
                return "DELIVERED";
            default:
                return null; // No mapping needed
        }
    }

    /**
     * Updates the order status with appropriate transition
     */
    private void updateOrderStatus(Order order, String newStatus) {
        try {
            // Find the order entity with metadata
            ModelSpec orderModelSpec = new ModelSpec()
                .withName(Order.ENTITY_NAME)
                .withVersion(Order.ENTITY_VERSION);

            SimpleCondition orderIdCondition = new SimpleCondition()
                .withJsonPath("$.orderId")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(order.getOrderId()));

            GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(orderIdCondition));

            List<EntityWithMetadata<Order>> orders = entityService.search(
                orderModelSpec, condition, Order.class);

            if (!orders.isEmpty()) {
                EntityWithMetadata<Order> orderWithMetadata = orders.get(0);
                Order orderEntity = orderWithMetadata.entity();
                
                // Update order status and timestamp
                orderEntity.setStatus(newStatus);
                orderEntity.setUpdatedAt(LocalDateTime.now());
                
                // Determine the appropriate transition based on the new status
                String transition = getTransitionForOrderStatus(newStatus);
                
                // Update the order
                entityService.update(orderWithMetadata.metadata().getId(), orderEntity, transition);
                
                logger.info("Updated order {} status to {} with transition {}", 
                           order.getOrderId(), newStatus, transition);
            }
        } catch (Exception e) {
            logger.error("Error updating order status for order: {}", order.getOrderId(), e);
        }
    }

    /**
     * Gets the appropriate transition name for the order status
     */
    private String getTransitionForOrderStatus(String orderStatus) {
        switch (orderStatus) {
            case "PICKING":
                return "START_PICKING";
            case "WAITING_TO_SEND":
                return "READY_TO_SEND";
            case "SENT":
                return "MARK_SENT";
            case "DELIVERED":
                return "MARK_DELIVERED";
            default:
                return null; // No transition needed
        }
    }
}
