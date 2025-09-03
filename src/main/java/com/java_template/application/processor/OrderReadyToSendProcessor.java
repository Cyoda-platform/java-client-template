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
public class OrderReadyToSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderReadyToSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderReadyToSendProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order ready to send for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract order entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract order entity: " + error.getMessage());
            })
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::processOrderReadyToSend)
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

    private Order processOrderReadyToSend(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Processing order ready to send: {}", order.getOrderId());
        
        try {
            // CRITICAL: The order entity already contains all the data we need
            // Never extract from request payload - use order getters directly
            
            // 1. Retrieve associated shipment by orderId
            Shipment shipment = retrieveShipmentByOrderId(order.getOrderId());
            if (shipment == null) {
                throw new RuntimeException("Shipment not found for order: " + order.getOrderId());
            }
            
            // 2. Update shipment quantities - mark all items as picked
            updateShipmentToPicked(shipment);
            
            // 3. Update order timestamp
            order.setUpdatedAt(Instant.now());
            
            logger.info("Order {} marked as ready to send, shipment {} updated", 
                order.getOrderId(), shipment.getShipmentId());
            
            return order;
            
        } catch (Exception e) {
            logger.error("Failed to process order ready to send: {}", e.getMessage(), e);
            throw new RuntimeException("Order ready to send processing failed: " + e.getMessage(), e);
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
    
    private void updateShipmentToPicked(Shipment shipment) {
        try {
            // Mark all lines as picked (qtyPicked = qtyOrdered)
            if (shipment.getLines() != null) {
                for (Shipment.ShipmentLine line : shipment.getLines()) {
                    if (line.getQtyOrdered() != null) {
                        line.setQtyPicked(line.getQtyOrdered());
                        logger.debug("Marked line {} as picked: {} units", line.getSku(), line.getQtyPicked());
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
                
                // Update shipment with ready_to_send transition
                entityService.update(shipmentTechnicalId, shipment, "ready_to_send");
                
                logger.info("Shipment {} updated to picked state", shipment.getShipmentId());
            } else {
                throw new RuntimeException("Could not find shipment for update: " + shipment.getShipmentId());
            }
            
        } catch (Exception e) {
            logger.error("Failed to update shipment {}: {}", shipment.getShipmentId(), e.getMessage());
            throw new RuntimeException("Shipment update failed", e);
        }
    }
}
