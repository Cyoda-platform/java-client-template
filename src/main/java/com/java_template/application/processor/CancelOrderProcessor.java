package com.java_template.application.processor;

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
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Processor to handle order cancellation
 * Releases reserved inventory and processes refunds
 */
@Component
public class CancelOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CancelOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CancelOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processOrderCancellation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processOrderCancellation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Cancelling order: {} from state: {}", order.getOrderId(), currentState);

        // Release reserved inventory if order was packed
        if ("packed".equals(currentState) || "paid".equals(currentState)) {
            releaseReservedInventory(order);
        }

        // Process refund if payment was captured
        if (order.getPayment() != null && "captured".equals(order.getPayment().getStatus())) {
            processRefund(order);
        }

        // Update line item fulfillment status
        updateLineItemFulfillmentStatus(order);

        // Update order timestamp
        order.setUpdatedTimestamp(LocalDateTime.now());
        order.setLastUpdatedBy("CancelOrderProcessor");

        logger.info("Order {} cancelled successfully", order.getOrderId());

        return entityWithMetadata;
    }

    private void releaseReservedInventory(Order order) {
        for (Order.LineItem lineItem : order.getLineItems()) {
            if ("reserved".equals(lineItem.getFulfilmentStatus()) || 
                "packed".equals(lineItem.getFulfilmentStatus())) {
                
                try {
                    releaseInventoryForLineItem(order, lineItem);
                    lineItem.setFulfilmentStatus("cancelled");
                } catch (Exception e) {
                    logger.error("Failed to release inventory for product {} in order {}", 
                                lineItem.getProductId(), order.getOrderId(), e);
                    lineItem.setFulfilmentStatus("release_failed");
                }
            }
        }
    }

    private void releaseInventoryForLineItem(Order order, Order.LineItem lineItem) {
        // Find inventory item by product ID
        InventoryItem inventoryItem = findInventoryItemByProductId(lineItem.getProductId());
        
        if (inventoryItem == null) {
            throw new RuntimeException("Inventory item not found for product: " + lineItem.getProductId());
        }

        // Release reserved stock
        releaseReservedStock(inventoryItem, lineItem.getQuantity(), order.getOrderId());
        
        logger.debug("Released {} units of product {} for cancelled order {}", 
                    lineItem.getQuantity(), lineItem.getProductId(), order.getOrderId());
    }

    private InventoryItem findInventoryItemByProductId(String productId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(InventoryItem.ENTITY_NAME)
                    .withVersion(InventoryItem.ENTITY_VERSION);

            EntityWithMetadata<InventoryItem> inventoryItemWithMetadata = entityService.findByBusinessId(
                    modelSpec, productId, "productId", InventoryItem.class);

            return inventoryItemWithMetadata != null ? inventoryItemWithMetadata.entity() : null;

        } catch (Exception e) {
            logger.error("Error finding inventory item for product: {}", productId, e);
            return null;
        }
    }

    private void releaseReservedStock(InventoryItem inventoryItem, Integer quantityToRelease, String orderId) {
        // Find location with reserved stock for this order
        InventoryItem.StockByLocation locationWithReservedStock = findLocationWithReservedStock(
                inventoryItem, quantityToRelease);

        if (locationWithReservedStock == null) {
            throw new RuntimeException("No reserved stock found for product: " + inventoryItem.getProductId());
        }

        // Update stock levels
        int currentAvailable = locationWithReservedStock.getAvailable();
        int currentReserved = locationWithReservedStock.getReserved();

        locationWithReservedStock.setAvailable(currentAvailable + quantityToRelease);
        locationWithReservedStock.setReserved(currentReserved - quantityToRelease);
        locationWithReservedStock.setLastStockCheck(LocalDateTime.now());
        locationWithReservedStock.setLastCheckedBy("CancelOrderProcessor");

        // Add audit log entry
        addAuditLogEntry(inventoryItem, "cancellation", "CancelOrderProcessor", 
                        locationWithReservedStock.getLocationId(), "available", 
                        quantityToRelease, currentAvailable, currentAvailable + quantityToRelease, orderId);

        // Update inventory item
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(InventoryItem.ENTITY_NAME)
                    .withVersion(InventoryItem.ENTITY_VERSION);

            EntityWithMetadata<InventoryItem> inventoryItemWithMetadata = entityService.findByBusinessId(
                    modelSpec, inventoryItem.getProductId(), "productId", InventoryItem.class);

            if (inventoryItemWithMetadata != null) {
                entityService.update(inventoryItemWithMetadata.metadata().getId(), 
                                   inventoryItem, "release_stock");
            }

        } catch (Exception e) {
            logger.error("Failed to update inventory item: {}", inventoryItem.getProductId(), e);
            throw new RuntimeException("Failed to update inventory", e);
        }
    }

    private InventoryItem.StockByLocation findLocationWithReservedStock(
            InventoryItem inventoryItem, Integer requiredQuantity) {
        
        if (inventoryItem.getStockByLocation() == null) {
            return null;
        }

        return inventoryItem.getStockByLocation().stream()
                .filter(stock -> stock.getReserved() != null && stock.getReserved() >= requiredQuantity)
                .findFirst()
                .orElse(null);
    }

    private void addAuditLogEntry(InventoryItem inventoryItem, String reason, String actor, 
                                 String locationId, String stockType, Integer delta, 
                                 Integer previousValue, Integer newValue, String referenceId) {
        
        if (inventoryItem.getAuditLog() == null) {
            inventoryItem.setAuditLog(new ArrayList<>());
        }

        InventoryItem.AuditLogEntry auditEntry = new InventoryItem.AuditLogEntry();
        auditEntry.setTimestamp(LocalDateTime.now());
        auditEntry.setReason(reason);
        auditEntry.setActor(actor);
        auditEntry.setLocationId(locationId);
        auditEntry.setStockType(stockType);
        auditEntry.setDelta(delta);
        auditEntry.setPreviousValue(previousValue);
        auditEntry.setNewValue(newValue);
        auditEntry.setReferenceId(referenceId);
        auditEntry.setNotes("Stock released due to order cancellation");

        inventoryItem.getAuditLog().add(auditEntry);
    }

    private void processRefund(Order order) {
        Order.Payment payment = order.getPayment();
        
        // Simulate refund processing
        logger.debug("Processing refund for order: {} amount: {}", order.getOrderId(), payment.getAmount());
        
        // In real implementation, this would call payment gateway for refund
        // For simulation, we'll mark payment as refunded
        payment.setStatus("refunded");
        payment.setFailureReason("Order cancelled - refund processed");
        
        logger.info("Refund processed for order: {}", order.getOrderId());
    }

    private void updateLineItemFulfillmentStatus(Order order) {
        for (Order.LineItem lineItem : order.getLineItems()) {
            if (!"cancelled".equals(lineItem.getFulfilmentStatus()) && 
                !"release_failed".equals(lineItem.getFulfilmentStatus())) {
                lineItem.setFulfilmentStatus("cancelled");
            }
        }
    }
}
