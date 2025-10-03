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
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor to pack orders by reserving inventory and deducting stock
 * Handles inventory reservation, stock deduction, and fulfillment task creation
 */
@Component
public class PackOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PackOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PackOrderProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processOrderPacking)
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

    private EntityWithMetadata<Order> processOrderPacking(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Packing order: {}", order.getOrderId());

        // Reserve inventory for each line item
        for (Order.LineItem lineItem : order.getLineItems()) {
            reserveInventoryForLineItem(order, lineItem);
        }

        // Update order timestamp
        order.setUpdatedTimestamp(LocalDateTime.now());
        order.setLastUpdatedBy("PackOrderProcessor");

        logger.info("Order {} packed successfully", order.getOrderId());

        return entityWithMetadata;
    }

    private void reserveInventoryForLineItem(Order order, Order.LineItem lineItem) {
        try {
            // Find inventory item by product ID
            InventoryItem inventoryItem = findInventoryItemByProductId(lineItem.getProductId());
            
            if (inventoryItem == null) {
                throw new RuntimeException("Inventory item not found for product: " + lineItem.getProductId());
            }

            // Reserve stock
            reserveStock(inventoryItem, lineItem.getQuantity(), order.getOrderId());
            
            // Update line item fulfillment status
            lineItem.setFulfilmentStatus("reserved");
            
            logger.debug("Reserved {} units of product {} for order {}", 
                        lineItem.getQuantity(), lineItem.getProductId(), order.getOrderId());

        } catch (Exception e) {
            logger.error("Failed to reserve inventory for product {} in order {}", 
                        lineItem.getProductId(), order.getOrderId(), e);
            lineItem.setFulfilmentStatus("reservation_failed");
            throw new RuntimeException("Inventory reservation failed for product: " + lineItem.getProductId(), e);
        }
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

    private void reserveStock(InventoryItem inventoryItem, Integer quantityToReserve, String orderId) {
        // Find location with sufficient available stock
        InventoryItem.StockByLocation locationWithStock = findLocationWithSufficientStock(
                inventoryItem, quantityToReserve);

        if (locationWithStock == null) {
            throw new RuntimeException("Insufficient stock available for product: " + inventoryItem.getProductId());
        }

        // Update stock levels
        int currentAvailable = locationWithStock.getAvailable();
        int currentReserved = locationWithStock.getReserved();

        locationWithStock.setAvailable(currentAvailable - quantityToReserve);
        locationWithStock.setReserved(currentReserved + quantityToReserve);
        locationWithStock.setLastStockCheck(LocalDateTime.now());
        locationWithStock.setLastCheckedBy("PackOrderProcessor");

        // Add audit log entry
        addAuditLogEntry(inventoryItem, "sale", "PackOrderProcessor", 
                        locationWithStock.getLocationId(), "reserved", 
                        quantityToReserve, currentReserved, currentReserved + quantityToReserve, orderId);

        // Update inventory item
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(InventoryItem.ENTITY_NAME)
                    .withVersion(InventoryItem.ENTITY_VERSION);

            EntityWithMetadata<InventoryItem> inventoryItemWithMetadata = entityService.findByBusinessId(
                    modelSpec, inventoryItem.getProductId(), "productId", InventoryItem.class);

            if (inventoryItemWithMetadata != null) {
                entityService.update(inventoryItemWithMetadata.metadata().getId(), 
                                   inventoryItem, "reserve_stock");
            }

        } catch (Exception e) {
            logger.error("Failed to update inventory item: {}", inventoryItem.getProductId(), e);
            throw new RuntimeException("Failed to update inventory", e);
        }
    }

    private InventoryItem.StockByLocation findLocationWithSufficientStock(
            InventoryItem inventoryItem, Integer requiredQuantity) {
        
        if (inventoryItem.getStockByLocation() == null) {
            return null;
        }

        return inventoryItem.getStockByLocation().stream()
                .filter(stock -> stock.getAvailable() != null && stock.getAvailable() >= requiredQuantity)
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
        auditEntry.setNotes("Stock reserved for order packing");

        inventoryItem.getAuditLog().add(auditEntry);
    }
}
