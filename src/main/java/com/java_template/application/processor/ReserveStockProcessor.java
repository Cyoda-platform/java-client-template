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
 * Processor to handle stock reservation for inventory items
 * Moves available stock to reserved status
 */
@Component
public class ReserveStockProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReserveStockProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReserveStockProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(InventoryItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid inventory item wrapper")
                .map(this::processStockReservation)
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

    private EntityWithMetadata<InventoryItem> processStockReservation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {

        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();

        logger.debug("Processing stock reservation for inventory item: {}", inventoryItem.getProductId());

        // Initialize collections if needed
        if (inventoryItem.getStockByLocation() == null) {
            inventoryItem.setStockByLocation(new ArrayList<>());
        }
        if (inventoryItem.getAuditLog() == null) {
            inventoryItem.setAuditLog(new ArrayList<>());
        }

        // Calculate total stock levels
        calculateTotalStockLevels(inventoryItem);

        // Update timestamps
        inventoryItem.setUpdatedAt(LocalDateTime.now());
        inventoryItem.setLastUpdatedBy("ReserveStockProcessor");

        logger.info("Stock reservation processed for inventory item: {} - Total Reserved: {}", 
                   inventoryItem.getProductId(), inventoryItem.getTotalReserved());

        return entityWithMetadata;
    }

    private void calculateTotalStockLevels(InventoryItem inventoryItem) {
        int totalAvailable = 0;
        int totalReserved = 0;
        int totalDamaged = 0;

        for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation()) {
            if (stock.getAvailable() != null) totalAvailable += stock.getAvailable();
            if (stock.getReserved() != null) totalReserved += stock.getReserved();
            if (stock.getDamaged() != null) totalDamaged += stock.getDamaged();
        }

        inventoryItem.setTotalAvailable(totalAvailable);
        inventoryItem.setTotalReserved(totalReserved);
        inventoryItem.setTotalDamaged(totalDamaged);
    }

    /**
     * Reserve stock for a specific quantity and reference
     */
    public void reserveStock(InventoryItem inventoryItem, Integer quantityToReserve, 
                           String referenceId, String actor) {
        
        if (quantityToReserve <= 0) {
            throw new IllegalArgumentException("Quantity to reserve must be positive");
        }

        // Find location with sufficient available stock
        InventoryItem.StockByLocation location = findLocationWithSufficientStock(inventoryItem, quantityToReserve);
        
        if (location == null) {
            throw new RuntimeException("Insufficient available stock for reservation. Required: " + quantityToReserve);
        }

        // Move stock from available to reserved
        int currentAvailable = location.getAvailable();
        int currentReserved = location.getReserved();
        
        location.setAvailable(currentAvailable - quantityToReserve);
        location.setReserved(currentReserved + quantityToReserve);
        location.setLastStockCheck(LocalDateTime.now());
        location.setLastCheckedBy(actor);

        // Add audit log entries for both changes
        addAuditLogEntry(inventoryItem, "reservation", actor, location.getLocationId(), 
                        "available", -quantityToReserve, currentAvailable, 
                        currentAvailable - quantityToReserve, referenceId);
        
        addAuditLogEntry(inventoryItem, "reservation", actor, location.getLocationId(), 
                        "reserved", quantityToReserve, currentReserved, 
                        currentReserved + quantityToReserve, referenceId);

        logger.debug("Reserved {} units for product {} at location {} (Reference: {})", 
                    quantityToReserve, inventoryItem.getProductId(), location.getLocationId(), referenceId);
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
        auditEntry.setNotes("Stock reservation processed");

        inventoryItem.getAuditLog().add(auditEntry);
    }
}
