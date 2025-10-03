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
 * Processor to handle stock release for inventory items
 * Moves reserved stock back to available status
 */
@Component
public class ReleaseStockProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseStockProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReleaseStockProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(InventoryItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid inventory item wrapper")
                .map(this::processStockRelease)
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

    private EntityWithMetadata<InventoryItem> processStockRelease(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {

        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();

        logger.debug("Processing stock release for inventory item: {}", inventoryItem.getProductId());

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
        inventoryItem.setLastUpdatedBy("ReleaseStockProcessor");

        logger.info("Stock release processed for inventory item: {} - Total Available: {}", 
                   inventoryItem.getProductId(), inventoryItem.getTotalAvailable());

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
     * Release reserved stock back to available
     */
    public void releaseStock(InventoryItem inventoryItem, Integer quantityToRelease, 
                           String referenceId, String actor) {
        
        if (quantityToRelease <= 0) {
            throw new IllegalArgumentException("Quantity to release must be positive");
        }

        // Find location with sufficient reserved stock
        InventoryItem.StockByLocation location = findLocationWithSufficientReservedStock(inventoryItem, quantityToRelease);
        
        if (location == null) {
            throw new RuntimeException("Insufficient reserved stock for release. Required: " + quantityToRelease);
        }

        // Move stock from reserved to available
        int currentAvailable = location.getAvailable();
        int currentReserved = location.getReserved();
        
        location.setAvailable(currentAvailable + quantityToRelease);
        location.setReserved(currentReserved - quantityToRelease);
        location.setLastStockCheck(LocalDateTime.now());
        location.setLastCheckedBy(actor);

        // Add audit log entries for both changes
        addAuditLogEntry(inventoryItem, "release", actor, location.getLocationId(), 
                        "reserved", -quantityToRelease, currentReserved, 
                        currentReserved - quantityToRelease, referenceId);
        
        addAuditLogEntry(inventoryItem, "release", actor, location.getLocationId(), 
                        "available", quantityToRelease, currentAvailable, 
                        currentAvailable + quantityToRelease, referenceId);

        logger.debug("Released {} units for product {} at location {} (Reference: {})", 
                    quantityToRelease, inventoryItem.getProductId(), location.getLocationId(), referenceId);
    }

    private InventoryItem.StockByLocation findLocationWithSufficientReservedStock(
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
        auditEntry.setNotes("Stock release processed");

        inventoryItem.getAuditLog().add(auditEntry);
    }
}
