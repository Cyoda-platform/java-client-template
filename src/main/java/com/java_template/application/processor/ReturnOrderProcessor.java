package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.application.entity.order.version_1.Order;
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
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: ReturnOrderProcessor handles order returns with inventory restocking,
 * refund processing, and return reason tracking.
 */
@Component
public class ReturnOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReturnOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReturnOrderProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processReturnOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Order entity is null");
            return false;
        }

        Order order = entityWithMetadata.entity();
        
        // Validate order can be returned (must be shipped or delivered)
        String currentState = entityWithMetadata.metadata().getState();
        if (!"Shipped".equals(currentState) && !"Delivered".equals(currentState)) {
            logger.error("Order cannot be returned from state: {}, orderId: {}", currentState, order.getOrderId());
            return false;
        }

        // Validate shipment information exists
        if (order.getShipment() == null) {
            logger.error("Shipment information is missing for return orderId: {}", order.getOrderId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<Order> processReturnOrder(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        logger.info("Processing return for order with orderId: {}, currentState: {}", order.getOrderId(), currentState);

        try {
            // Restock returned inventory
            restockReturnedInventory(order);

            // Process refund
            if (order.getPayment() != null && "captured".equals(order.getPayment().getStatus())) {
                processReturnRefund(order);
            }

            // Update line items to returned status
            if (order.getLineItems() != null) {
                order.getLineItems().forEach(item -> item.setFulfilmentStatus("returned"));
            }

            // Add return event to shipment
            if (order.getShipment().getEvents() == null) {
                order.getShipment().setEvents(new ArrayList<>());
            }

            Order.ShipmentEvent returnEvent = new Order.ShipmentEvent();
            returnEvent.setEventType("returned");
            returnEvent.setDescription("Order returned by customer");
            returnEvent.setTimestamp(LocalDateTime.now());
            returnEvent.setLocation("Return Center");
            order.getShipment().getEvents().add(returnEvent);

            logger.info("Order returned successfully - orderId: {}, previousState: {}", 
                       order.getOrderId(), currentState);

            return new EntityWithMetadata<>(order, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing return for orderId: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to return order: " + e.getMessage(), e);
        }
    }

    private void restockReturnedInventory(Order order) {
        logger.info("Restocking returned inventory for order: {}", order.getOrderId());

        for (Order.LineItem lineItem : order.getLineItems()) {
            try {
                // Find inventory item by productId
                ModelSpec inventoryModelSpec = new ModelSpec();
                inventoryModelSpec.setName(InventoryItem.ENTITY_NAME);
                inventoryModelSpec.setVersion(InventoryItem.ENTITY_VERSION);

                List<QueryCondition> conditions = new ArrayList<>();
                SimpleCondition productCondition = new SimpleCondition()
                        .withJsonPath("$.productId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(lineItem.getProductId()));
                conditions.add(productCondition);

                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);

                List<EntityWithMetadata<InventoryItem>> inventoryItems = 
                    entityService.search(inventoryModelSpec, groupCondition, InventoryItem.class);

                if (!inventoryItems.isEmpty()) {
                    EntityWithMetadata<InventoryItem> inventoryItemWithMetadata = inventoryItems.get(0);
                    InventoryItem inventoryItem = inventoryItemWithMetadata.entity();

                    // Add returned stock back to available (simplified - using first location)
                    String locationId = inventoryItem.getStockByLocation().keySet().iterator().next();
                    InventoryItem.StockByLocation stock = inventoryItem.getStockByLocation().get(locationId);
                    
                    stock.setAvailable(stock.getAvailable() + lineItem.getQuantity());
                    stock.setLastUpdated(LocalDateTime.now());

                    // Add audit log entry
                    InventoryItem.AuditLogEntry auditEntry = new InventoryItem.AuditLogEntry();
                    auditEntry.setEntryId(UUID.randomUUID().toString());
                    auditEntry.setReason("order_return");
                    auditEntry.setActor("ReturnOrderProcessor");
                    auditEntry.setLocationId(locationId);
                    auditEntry.setTimestamp(LocalDateTime.now());
                    auditEntry.setReferenceId(order.getOrderId());
                    auditEntry.setNotes("Restocked due to order return");

                    InventoryItem.StockDelta delta = new InventoryItem.StockDelta();
                    delta.setAvailableDelta(lineItem.getQuantity());
                    auditEntry.setDelta(delta);

                    if (inventoryItem.getAuditLog() == null) {
                        inventoryItem.setAuditLog(new ArrayList<>());
                    }
                    inventoryItem.getAuditLog().add(auditEntry);

                    // Update inventory item
                    entityService.update(inventoryItemWithMetadata.metadata().getId(), inventoryItem, "adjust_stock");
                    
                    logger.info("Restocked {} units of productId: {} for returned order: {}", 
                               lineItem.getQuantity(), lineItem.getProductId(), order.getOrderId());
                }
            } catch (Exception e) {
                logger.error("Failed to restock inventory for productId: {}, orderId: {}", 
                            lineItem.getProductId(), order.getOrderId(), e);
                // Continue with other items even if one fails
            }
        }
    }

    private void processReturnRefund(Order order) {
        logger.info("Processing return refund for order: {}", order.getOrderId());

        try {
            Order.Payment payment = order.getPayment();
            
            // Update payment status to refunded
            payment.setStatus("refunded");
            
            // Set refund timestamp
            if (payment.getTimestamps() != null) {
                payment.getTimestamps().setRefunded(LocalDateTime.now());
            }

            logger.info("Return refund processed for orderId: {}, amount: {}", 
                       order.getOrderId(), payment.getAmount());

        } catch (Exception e) {
            logger.error("Failed to process return refund for orderId: {}", order.getOrderId(), e);
            // Don't fail the entire return if refund fails
        }
    }
}
