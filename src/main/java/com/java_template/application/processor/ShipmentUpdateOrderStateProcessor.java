package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ShipmentUpdateOrderStateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentUpdateOrderStateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    
    @Autowired
    private EntityService entityService;

    public ShipmentUpdateOrderStateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment order state update for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentOrderStateUpdate)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null && 
               shipment.getShipmentId() != null && !shipment.getShipmentId().trim().isEmpty() &&
               shipment.getOrderId() != null && !shipment.getOrderId().trim().isEmpty();
    }

    private Shipment processShipmentOrderStateUpdate(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        EntityProcessorCalculationRequest request = context.request();
        
        logger.info("Updating order state based on shipment {} state change", shipment.getShipmentId());

        try {
            // Find the associated order
            Order order = findOrderByOrderId(shipment.getOrderId());
            UUID orderId = getOrderEntityId(shipment.getOrderId());

            // Get current shipment state from request metadata
            String shipmentState = getCurrentShipmentState(request);
            
            // Map shipment state to order state and transition
            StateMapping stateMapping = mapShipmentStateToOrder(shipmentState);
            
            if (stateMapping == null) {
                logger.warn("Unknown shipment state: {}", shipmentState);
                return updateShipmentTimestamp(shipment);
            }

            // Get current order state
            String currentOrderState = getCurrentOrderState(orderId);
            
            // Update order state if different
            if (!stateMapping.targetOrderState.equals(currentOrderState)) {
                updateOrderState(orderId, order, stateMapping.orderTransition);
                logger.info("Order {} state updated to {} following shipment state change", 
                           order.getOrderId(), stateMapping.targetOrderState);
            } else {
                logger.debug("Order {} already in target state {}", order.getOrderId(), stateMapping.targetOrderState);
            }

            return updateShipmentTimestamp(shipment);

        } catch (Exception e) {
            logger.error("Failed to update order state for shipment {}: {}", shipment.getShipmentId(), e.getMessage());
            // Continue with shipment update even if order update fails
            return updateShipmentTimestamp(shipment);
        }
    }

    private Order findOrderByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
                Order.class,
                Order.ENTITY_NAME,
                Order.ENTITY_VERSION,
                condition,
                true
            );

            if (orderResponse.isEmpty()) {
                throw new RuntimeException("Order not found: " + orderId);
            }

            return orderResponse.get().getData();
        } catch (Exception e) {
            logger.error("Failed to find order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to find order: " + e.getMessage(), e);
        }
    }

    private UUID getOrderEntityId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
                Order.class,
                Order.ENTITY_NAME,
                Order.ENTITY_VERSION,
                condition,
                true
            );

            if (orderResponse.isEmpty()) {
                throw new RuntimeException("Order not found: " + orderId);
            }

            return orderResponse.get().getMetadata().getId();
        } catch (Exception e) {
            logger.error("Failed to get order entity ID for {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to get order entity ID: " + e.getMessage(), e);
        }
    }

    private String getCurrentShipmentState(EntityProcessorCalculationRequest request) {
        // In a real implementation, we would extract the current state from the request metadata
        // For now, we'll use a placeholder - this would typically come from request.getPayload().getMeta().get("state")
        return "PICKING"; // This should be extracted from the actual request
    }

    private String getCurrentOrderState(UUID orderId) {
        try {
            EntityResponse<Order> orderResponse = entityService.getItem(orderId, Order.class);
            return orderResponse.getMetadata().getState();
        } catch (Exception e) {
            logger.error("Failed to get current order state: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    private StateMapping mapShipmentStateToOrder(String shipmentState) {
        return switch (shipmentState) {
            case "PICKING" -> new StateMapping("PICKING", "ready_to_send");
            case "WAITING_TO_SEND" -> new StateMapping("WAITING_TO_SEND", "ready_to_send");
            case "SENT" -> new StateMapping("SENT", "mark_sent");
            case "DELIVERED" -> new StateMapping("DELIVERED", "mark_delivered");
            default -> null;
        };
    }

    private void updateOrderState(UUID orderId, Order order, String transition) {
        try {
            order.setUpdatedAt(LocalDateTime.now());
            entityService.update(orderId, order, transition);
        } catch (Exception e) {
            logger.error("Failed to update order state: {}", e.getMessage());
            throw new RuntimeException("Failed to update order state: " + e.getMessage(), e);
        }
    }

    private Shipment updateShipmentTimestamp(Shipment shipment) {
        shipment.setUpdatedAt(LocalDateTime.now());
        return shipment;
    }

    private static class StateMapping {
        final String targetOrderState;
        final String orderTransition;

        StateMapping(String targetOrderState, String orderTransition) {
            this.targetOrderState = targetOrderState;
            this.orderTransition = orderTransition;
        }
    }
}
