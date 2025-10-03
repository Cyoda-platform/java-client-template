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
 * Processor to handle order returns
 * Processes refunds and restocks returned inventory
 */
@Component
public class ReturnOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReturnOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReturnOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processOrderReturn)
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

    private EntityWithMetadata<Order> processOrderReturn(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing return for order: {}", order.getOrderId());

        // Process refund if payment was captured
        if (order.getPayment() != null && "captured".equals(order.getPayment().getStatus())) {
            processRefund(order);
        }

        // Restock returned inventory
        restockReturnedInventory(order);

        // Update line item fulfillment status
        updateLineItemFulfillmentStatus(order);

        // Update order timestamp
        order.setUpdatedTimestamp(LocalDateTime.now());
        order.setLastUpdatedBy("ReturnOrderProcessor");

        logger.info("Order {} return processed successfully", order.getOrderId());

        return entityWithMetadata;
    }

    private void processRefund(Order order) {
        Order.Payment payment = order.getPayment();
        
        // Simulate refund processing
        logger.debug("Processing refund for returned order: {} amount: {}", order.getOrderId(), payment.getAmount());
        
        // In real implementation, this would call payment gateway for refund
        payment.setStatus("refunded");
        payment.setFailureReason("Order returned - refund processed");
        
        logger.info("Refund processed for returned order: {}", order.getOrderId());
    }

    private void restockReturnedInventory(Order order) {
        for (Order.LineItem lineItem : order.getLineItems()) {
            if ("delivered".equals(lineItem.getFulfilmentStatus()) || 
                "shipped".equals(lineItem.getFulfilmentStatus())) {
                
                try {
                    restockInventoryForLineItem(order, lineItem);
                    lineItem.setFulfilmentStatus("returned");
                } catch (Exception e) {
                    logger.error("Failed to restock inventory for product {} in returned order {}", 
                                lineItem.getProductId(), order.getOrderId(), e);
                    lineItem.setFulfilmentStatus("restock_failed");
                }
            }
        }
    }

    private void restockInventoryForLineItem(Order order, Order.LineItem lineItem) {
        // Find inventory item by product ID
        InventoryItem inventoryItem = findInventoryItemByProductId(lineItem.getProductId());
        
        if (inventoryItem == null) {
            throw new RuntimeException("Inventory item not found for product: " + lineItem.getProductId());
        }

        // Restock returned items
        restockInventory(inventoryItem, lineItem.getQuantity(), order.getOrderId());
        
        logger.debug("Restocked {} units of product {} for returned order {}", 
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

    private void restockInventory(InventoryItem inventoryItem, Integer quantityToRestock, String orderId) {
        // Find a location to restock (prefer first location or create default)
        InventoryItem.StockByLocation restockLocation = findRestockLocation(inventoryItem);

        if (restockLocation == null) {
            // Create default location if none exists
            restockLocation = createDefaultLocation();
            if (inventoryItem.getStockByLocation() == null) {
                inventoryItem.setStockByLocation(new ArrayList<>());
            }
            inventoryItem.getStockByLocation().add(restockLocation);
        }

        // Update stock levels
        int currentAvailable = restockLocation.getAvailable() != null ? restockLocation.getAvailable() : 0;
        restockLocation.setAvailable(currentAvailable + quantityToRestock);
        restockLocation.setLastStockCheck(LocalDateTime.now());
        restockLocation.setLastCheckedBy("ReturnOrderProcessor");

        // Add audit log entry
        addAuditLogEntry(inventoryItem, "return", "ReturnOrderProcessor", 
                        restockLocation.getLocationId(), "available", 
                        quantityToRestock, currentAvailable, currentAvailable + quantityToRestock, orderId);

        // Update inventory item
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(InventoryItem.ENTITY_NAME)
                    .withVersion(InventoryItem.ENTITY_VERSION);

            EntityWithMetadata<InventoryItem> inventoryItemWithMetadata = entityService.findByBusinessId(
                    modelSpec, inventoryItem.getProductId(), "productId", InventoryItem.class);

            if (inventoryItemWithMetadata != null) {
                entityService.update(inventoryItemWithMetadata.metadata().getId(), 
                                   inventoryItem, "adjust_stock");
            }

        } catch (Exception e) {
            logger.error("Failed to update inventory item: {}", inventoryItem.getProductId(), e);
            throw new RuntimeException("Failed to update inventory", e);
        }
    }

    private InventoryItem.StockByLocation findRestockLocation(InventoryItem inventoryItem) {
        if (inventoryItem.getStockByLocation() == null || inventoryItem.getStockByLocation().isEmpty()) {
            return null;
        }
        
        // Return first available location
        return inventoryItem.getStockByLocation().get(0);
    }

    private InventoryItem.StockByLocation createDefaultLocation() {
        InventoryItem.StockByLocation location = new InventoryItem.StockByLocation();
        location.setLocationId("WAREHOUSE-01");
        location.setLocationName("Main Warehouse");
        location.setLocationType("warehouse");
        location.setAvailable(0);
        location.setReserved(0);
        location.setDamaged(0);
        location.setInTransit(0);
        location.setLastStockCheck(LocalDateTime.now());
        location.setLastCheckedBy("ReturnOrderProcessor");
        return location;
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
        auditEntry.setNotes("Stock restocked due to order return");

        inventoryItem.getAuditLog().add(auditEntry);
    }

    private void updateLineItemFulfillmentStatus(Order order) {
        for (Order.LineItem lineItem : order.getLineItems()) {
            if (!"returned".equals(lineItem.getFulfilmentStatus()) && 
                !"restock_failed".equals(lineItem.getFulfilmentStatus())) {
                lineItem.setFulfilmentStatus("returned");
            }
        }
    }
}
