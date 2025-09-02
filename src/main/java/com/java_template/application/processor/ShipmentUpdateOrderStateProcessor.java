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

import java.time.Instant;
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
        logger.info("Processing Shipment update order state for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::updateOrderState)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null && 
               shipment.getOrderId() != null && !shipment.getOrderId().trim().isEmpty();
    }

    private Shipment updateOrderState(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        logger.info("Updating order state for shipment: {}", shipment.getShipmentId());

        try {
            // Get the order entity
            Order order = getOrderById(shipment.getOrderId());
            UUID orderTechnicalId = getOrderTechnicalId(shipment.getOrderId());

            // Determine order transition based on shipment state
            String orderTransition = determineOrderTransition(context);

            if (orderTransition != null) {
                // Update order entity with corresponding transition
                entityService.update(orderTechnicalId, order, orderTransition);
                logger.info("Order state updated with transition: {} for shipment: {}", 
                           orderTransition, shipment.getShipmentId());
            }

            // Update shipment timestamp
            shipment.setUpdatedAt(Instant.now());

        } catch (Exception e) {
            logger.error("Failed to update order state for shipment: {}", shipment.getShipmentId(), e);
            throw new RuntimeException("Failed to update order state: " + e.getMessage(), e);
        }

        return shipment;
    }

    private Order getOrderById(String orderId) {
        Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(orderIdCondition));

        Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
            Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, condition, true);
        
        if (orderResponse.isEmpty()) {
            throw new RuntimeException("Order not found: " + orderId);
        }
        
        return orderResponse.get().getData();
    }

    private UUID getOrderTechnicalId(String orderId) {
        Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(orderIdCondition));

        Optional<EntityResponse<Order>> orderResponse = entityService.getFirstItemByCondition(
            Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, condition, true);
        
        if (orderResponse.isEmpty()) {
            throw new RuntimeException("Order not found: " + orderId);
        }
        
        return orderResponse.get().getMetadata().getId();
    }

    private String determineOrderTransition(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        // Get shipment state from context
        // In a real implementation, we would get this from context.getEvent().getPayload().getMeta().get("state")
        // For now, we'll determine based on the processor being called
        
        EntityProcessorCalculationRequest request = context.getRequest();
        String transitionName = null;
        
        if (request.getTransition() != null) {
            String shipmentTransition = request.getTransition().getName();
            
            // Map shipment transitions to order transitions
            switch (shipmentTransition) {
                case "READY_TO_SEND":
                    transitionName = "READY_TO_SEND";
                    break;
                case "MARK_SENT":
                    transitionName = "MARK_SENT";
                    break;
                case "MARK_DELIVERED":
                    transitionName = "MARK_DELIVERED";
                    break;
                default:
                    logger.warn("Unknown shipment transition: {}", shipmentTransition);
                    break;
            }
        }

        return transitionName;
    }
}
