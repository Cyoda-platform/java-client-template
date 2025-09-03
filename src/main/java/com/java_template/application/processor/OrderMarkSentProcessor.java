package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderMarkSentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderMarkSentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderMarkSentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order mark sent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract order entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract order entity: " + error.getMessage());
            })
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::processOrderMarkSent)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && 
               order.getOrderId() != null && !order.getOrderId().trim().isEmpty();
    }

    private Order processOrderMarkSent(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Processing order mark sent: {}", order.getOrderId());
        
        try {
            // CRITICAL: The order entity already contains all the data we need
            // Never extract from request payload - use order getters directly
            
            // 1. Retrieve associated shipment by orderId
            Shipment shipment = retrieveShipmentByOrderId(order.getOrderId());
            if (shipment == null) {
                throw new RuntimeException("Shipment not found for order: " + order.getOrderId());
            }
            
            // 2. Update shipment quantities - mark all items as shipped
            updateShipmentToShipped(shipment);
            
            // 3. Update order timestamp
            order.setUpdatedAt(Instant.now());
            
            logger.info("Order {} marked as sent, shipment {} updated", 
                order.getOrderId(), shipment.getShipmentId());
            
            return order;
            
        } catch (Exception e) {
            logger.error("Failed to process order mark sent: {}", e.getMessage(), e);
            throw new RuntimeException("Order mark sent processing failed: " + e.getMessage(), e);
        }
    }
    
    private Shipment retrieveShipmentByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", orderIdCondition);
            
            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);
                
            return shipmentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to retrieve shipment for order {}: {}", orderId, e.getMessage());
            return null;
        }
    }
    
    private void updateShipmentToShipped(Shipment shipment) {
        try {
            // Mark all lines as shipped (qtyShipped = qtyPicked)
            if (shipment.getLines() != null) {
                for (Shipment.ShipmentLine line : shipment.getLines()) {
                    if (line.getQtyPicked() != null) {
                        line.setQtyShipped(line.getQtyPicked());
                        logger.debug("Marked line {} as shipped: {} units", line.getSku(), line.getQtyShipped());
                    }
                }
            }
            
            shipment.setUpdatedAt(Instant.now());
            
            // Find shipment by condition to get its technical ID
            Condition shipmentIdCondition = Condition.of("$.shipmentId", "EQUALS", shipment.getShipmentId());
            SearchConditionRequest condition = SearchConditionRequest.group("AND", shipmentIdCondition);
            
            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);
            
            if (shipmentResponse.isPresent()) {
                UUID shipmentTechnicalId = shipmentResponse.get().getMetadata().getId();
                
                // Update shipment with mark_sent transition
                entityService.update(shipmentTechnicalId, shipment, "mark_sent");
                
                logger.info("Shipment {} updated to shipped state", shipment.getShipmentId());
            } else {
                throw new RuntimeException("Could not find shipment for update: " + shipment.getShipmentId());
            }
            
        } catch (Exception e) {
            logger.error("Failed to update shipment {}: {}", shipment.getShipmentId(), e.getMessage());
            throw new RuntimeException("Shipment update failed", e);
        }
    }
}
