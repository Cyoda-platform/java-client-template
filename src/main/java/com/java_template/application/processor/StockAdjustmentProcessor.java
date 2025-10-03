package com.java_template.application.processor;

import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Processor to handle stock adjustments for inventory items
 * Manages stock level changes and maintains audit trail
 */
@Component
public class StockAdjustmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StockAdjustmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StockAdjustmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(InventoryItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid inventory item wrapper")
                .map(this::processStockAdjustment)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<InventoryItem> entityWithMetadata) {
        InventoryItem inventoryItem = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return inventoryItem != null && inventoryItem.isValid() && technicalId != null;
    }

    private EntityWithMetadata<InventoryItem> processStockAdjustment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {

        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();

        logger.debug("Processing stock adjustment for inventory item: {}", inventoryItem.getProductId());

        // Initialize stock locations if not exists
        if (inventoryItem.getStockByLocation() == null) {
            inventoryItem.setStockByLocation(new ArrayList<>());
        }

        // Initialize audit log if not exists
        if (inventoryItem.getAuditLog() == null) {
            inventoryItem.setAuditLog(new ArrayList<>());
        }

        // Ensure at least one location exists
        ensureDefaultLocationExists(inventoryItem);

        // Calculate total stock levels
        calculateTotalStockLevels(inventoryItem);

        // Update timestamps
        inventoryItem.setUpdatedAt(LocalDateTime.now());
        inventoryItem.setLastUpdatedBy("StockAdjustmentProcessor");

        // Update last stock check for all locations
        updateLastStockCheck(inventoryItem);

        logger.info("Stock adjustment processed for inventory item: {} - Total Available: {}", 
                   inventoryItem.getProductId(), inventoryItem.getTotalAvailable());

        return entityWithMetadata;
    }

    private void ensureDefaultLocationExists(InventoryItem inventoryItem) {
        if (inventoryItem.getStockByLocation().isEmpty()) {
            InventoryItem.StockByLocation defaultLocation = createDefaultLocation();
            inventoryItem.getStockByLocation().add(defaultLocation);
            
            // Add audit log for location creation
            addAuditLogEntry(inventoryItem, "location_created", "StockAdjustmentProcessor", 
                           defaultLocation.getLocationId(), "available", 0, 0, 0, null);
        }
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
        location.setLastCheckedBy("StockAdjustmentProcessor");
        return location;
    }

    private void calculateTotalStockLevels(InventoryItem inventoryItem) {
        int totalAvailable = 0;
        int totalReserved = 0;
        int totalDamaged = 0;

        for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation()) {
            // Ensure non-null values
            if (stock.getAvailable() == null) stock.setAvailable(0);
            if (stock.getReserved() == null) stock.setReserved(0);
            if (stock.getDamaged() == null) stock.setDamaged(0);
            if (stock.getInTransit() == null) stock.setInTransit(0);

            totalAvailable += stock.getAvailable();
            totalReserved += stock.getReserved();
            totalDamaged += stock.getDamaged();
        }

        inventoryItem.setTotalAvailable(totalAvailable);
        inventoryItem.setTotalReserved(totalReserved);
        inventoryItem.setTotalDamaged(totalDamaged);
    }

    private void updateLastStockCheck(InventoryItem inventoryItem) {
        LocalDateTime now = LocalDateTime.now();
        for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation()) {
            stock.setLastStockCheck(now);
            if (stock.getLastCheckedBy() == null) {
                stock.setLastCheckedBy("StockAdjustmentProcessor");
            }
        }
    }

    private void addAuditLogEntry(InventoryItem inventoryItem, String reason, String actor, 
                                 String locationId, String stockType, Integer delta, 
                                 Integer previousValue, Integer newValue, String referenceId) {
        
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
        auditEntry.setNotes("Stock adjustment processed");

        inventoryItem.getAuditLog().add(auditEntry);
    }

    /**
     * Helper method to adjust stock for a specific location and stock type
     * This would be called by external systems or other processors
     */
    public void adjustStock(InventoryItem inventoryItem, String locationId, String stockType, 
                           Integer adjustment, String reason, String actor, String referenceId) {
        
        InventoryItem.StockByLocation location = findLocationById(inventoryItem, locationId);
        if (location == null) {
            throw new IllegalArgumentException("Location not found: " + locationId);
        }

        Integer previousValue;
        Integer newValue;

        switch (stockType.toLowerCase()) {
            case "available":
                previousValue = location.getAvailable();
                newValue = previousValue + adjustment;
                if (newValue < 0) {
                    throw new IllegalArgumentException("Available stock cannot be negative");
                }
                location.setAvailable(newValue);
                break;
            case "reserved":
                previousValue = location.getReserved();
                newValue = previousValue + adjustment;
                if (newValue < 0) {
                    throw new IllegalArgumentException("Reserved stock cannot be negative");
                }
                location.setReserved(newValue);
                break;
            case "damaged":
                previousValue = location.getDamaged();
                newValue = previousValue + adjustment;
                if (newValue < 0) {
                    throw new IllegalArgumentException("Damaged stock cannot be negative");
                }
                location.setDamaged(newValue);
                break;
            default:
                throw new IllegalArgumentException("Invalid stock type: " + stockType);
        }

        // Update location metadata
        location.setLastStockCheck(LocalDateTime.now());
        location.setLastCheckedBy(actor);

        // Add audit log entry
        addAuditLogEntry(inventoryItem, reason, actor, locationId, stockType, 
                        adjustment, previousValue, newValue, referenceId);

        logger.debug("Stock adjusted for product {} at location {}: {} {} changed from {} to {}", 
                    inventoryItem.getProductId(), locationId, stockType, adjustment, previousValue, newValue);
    }

    private InventoryItem.StockByLocation findLocationById(InventoryItem inventoryItem, String locationId) {
        if (inventoryItem.getStockByLocation() == null) {
            return null;
        }
        
        return inventoryItem.getStockByLocation().stream()
                .filter(stock -> locationId.equals(stock.getLocationId()))
                .findFirst()
                .orElse(null);
    }
}
