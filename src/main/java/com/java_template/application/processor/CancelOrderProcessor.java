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
 * ABOUTME: CancelOrderProcessor handles order cancellation with inventory adjustment,
 * payment refund processing, and reason code tracking.
 */
@Component
public class CancelOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CancelOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CancelOrderProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
                .map(this::processCancelOrder)
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
        
        // Validate order can be cancelled (not already delivered)
        String currentState = entityWithMetadata.metadata().getState();
        if ("Delivered".equals(currentState) || "Cancelled".equals(currentState) || "Returned".equals(currentState)) {
            logger.error("Order cannot be cancelled from state: {}, orderId: {}", currentState, order.getOrderId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<Order> processCancelOrder(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        logger.info("Processing cancellation for order with orderId: {}, currentState: {}", order.getOrderId(), currentState);

        try {
            // Release reserved inventory if order was packed or paid
            if ("Packed".equals(currentState) || "Shipped".equals(currentState)) {
                releaseReservedInventory(order);
            }

            // Process refund if payment was captured
            if (order.getPayment() != null && "captured".equals(order.getPayment().getStatus())) {
                processRefund(order);
            }

            // Update line items to cancelled status
            if (order.getLineItems() != null) {
                order.getLineItems().forEach(item -> item.setFulfilmentStatus("cancelled"));
            }

            // Add cancellation event to shipment if it exists
            if (order.getShipment() != null) {
                if (order.getShipment().getEvents() == null) {
                    order.getShipment().setEvents(new ArrayList<>());
                }

                Order.ShipmentEvent cancelEvent = new Order.ShipmentEvent();
                cancelEvent.setEventType("cancelled");
                cancelEvent.setDescription("Order cancelled by customer/system");
                cancelEvent.setTimestamp(LocalDateTime.now());
                cancelEvent.setLocation("System");
                order.getShipment().getEvents().add(cancelEvent);
            }

            logger.info("Order cancelled successfully - orderId: {}, previousState: {}", 
                       order.getOrderId(), currentState);

            return new EntityWithMetadata<>(order, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing cancellation for orderId: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to cancel order: " + e.getMessage(), e);
        }
    }

    private void releaseReservedInventory(Order order) {
        logger.info("Releasing reserved inventory for cancelled order: {}", order.getOrderId());

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

                    // Release reserved stock back to available
                    for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation().values()) {
                        if (stock.getReserved() >= lineItem.getQuantity()) {
                            stock.setReserved(stock.getReserved() - lineItem.getQuantity());
                            stock.setAvailable(stock.getAvailable() + lineItem.getQuantity());
                            stock.setLastUpdated(LocalDateTime.now());

                            // Add audit log entry
                            InventoryItem.AuditLogEntry auditEntry = new InventoryItem.AuditLogEntry();
                            auditEntry.setEntryId(UUID.randomUUID().toString());
                            auditEntry.setReason("order_cancellation");
                            auditEntry.setActor("CancelOrderProcessor");
                            auditEntry.setLocationId(stock.getLocationId());
                            auditEntry.setTimestamp(LocalDateTime.now());
                            auditEntry.setReferenceId(order.getOrderId());
                            auditEntry.setNotes("Released reservation due to order cancellation");

                            InventoryItem.StockDelta delta = new InventoryItem.StockDelta();
                            delta.setAvailableDelta(lineItem.getQuantity());
                            delta.setReservedDelta(-lineItem.getQuantity());
                            auditEntry.setDelta(delta);

                            if (inventoryItem.getAuditLog() == null) {
                                inventoryItem.setAuditLog(new ArrayList<>());
                            }
                            inventoryItem.getAuditLog().add(auditEntry);

                            // Update inventory item
                            entityService.update(inventoryItemWithMetadata.metadata().getId(), inventoryItem, "adjust_stock");
                            
                            logger.info("Released {} units of productId: {} for cancelled order: {}", 
                                       lineItem.getQuantity(), lineItem.getProductId(), order.getOrderId());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to release inventory for productId: {}, orderId: {}", 
                            lineItem.getProductId(), order.getOrderId(), e);
                // Continue with other items even if one fails
            }
        }
    }

    private void processRefund(Order order) {
        logger.info("Processing refund for cancelled order: {}", order.getOrderId());

        try {
            Order.Payment payment = order.getPayment();
            
            // Update payment status to refunded
            payment.setStatus("refunded");
            
            // Set refund timestamp
            if (payment.getTimestamps() != null) {
                payment.getTimestamps().setRefunded(LocalDateTime.now());
            }

            logger.info("Refund processed for orderId: {}, amount: {}", 
                       order.getOrderId(), payment.getAmount());

        } catch (Exception e) {
            logger.error("Failed to process refund for orderId: {}", order.getOrderId(), e);
            // Don't fail the entire cancellation if refund fails
        }
    }
}
