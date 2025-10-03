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
 * Processor to handle general inventory item updates
 * Updates metadata, validates data, and maintains audit trail
 */
@Component
public class UpdateInventoryItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateInventoryItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateInventoryItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(InventoryItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid inventory item wrapper")
                .map(this::processInventoryItemUpdate)
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

    private EntityWithMetadata<InventoryItem> processInventoryItemUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {

        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();

        logger.debug("Processing update for inventory item: {}", inventoryItem.getProductId());

        // Initialize collections if needed
        if (inventoryItem.getStockByLocation() == null) {
            inventoryItem.setStockByLocation(new ArrayList<>());
        }
        if (inventoryItem.getAuditLog() == null) {
            inventoryItem.setAuditLog(new ArrayList<>());
        }

        // Validate and normalize data
        validateAndNormalizeData(inventoryItem);

        // Calculate total stock levels
        calculateTotalStockLevels(inventoryItem);

        // Check for alerts and warnings
        checkForAlerts(inventoryItem);

        // Update timestamps
        inventoryItem.setUpdatedAt(LocalDateTime.now());
        inventoryItem.setLastUpdatedBy("UpdateInventoryItemProcessor");

        // Add audit log entry for update
        addUpdateAuditEntry(inventoryItem);

        logger.info("Inventory item update processed: {}", inventoryItem.getProductId());

        return entityWithMetadata;
    }

    private void validateAndNormalizeData(InventoryItem inventoryItem) {
        // Ensure required fields are not null or empty
        if (inventoryItem.getProductId() == null || inventoryItem.getProductId().trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }

        if (inventoryItem.getSku() == null || inventoryItem.getSku().trim().isEmpty()) {
            throw new IllegalArgumentException("SKU cannot be null or empty");
        }

        // Normalize SKU to uppercase
        inventoryItem.setSku(inventoryItem.getSku().toUpperCase());

        // Validate reorder points
        if (inventoryItem.getReorderPoint() != null && inventoryItem.getReorderPoint() < 0) {
            throw new IllegalArgumentException("Reorder point cannot be negative");
        }

        if (inventoryItem.getReorderQuantity() != null && inventoryItem.getReorderQuantity() <= 0) {
            throw new IllegalArgumentException("Reorder quantity must be positive");
        }

        // Initialize attributes if null
        if (inventoryItem.getAttributes() == null) {
            inventoryItem.setAttributes(new InventoryItem.ProductAttributes());
        }

        // Set creation timestamp if not set
        if (inventoryItem.getCreatedAt() == null) {
            inventoryItem.setCreatedAt(LocalDateTime.now());
        }
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

            // Validate non-negative values
            if (stock.getAvailable() < 0 || stock.getReserved() < 0 || stock.getDamaged() < 0) {
                throw new IllegalArgumentException("Stock levels cannot be negative");
            }

            totalAvailable += stock.getAvailable();
            totalReserved += stock.getReserved();
            totalDamaged += stock.getDamaged();

            // Update location metadata
            if (stock.getLastStockCheck() == null) {
                stock.setLastStockCheck(LocalDateTime.now());
            }
            if (stock.getLastCheckedBy() == null) {
                stock.setLastCheckedBy("UpdateInventoryItemProcessor");
            }
        }

        inventoryItem.setTotalAvailable(totalAvailable);
        inventoryItem.setTotalReserved(totalReserved);
        inventoryItem.setTotalDamaged(totalDamaged);
    }

    private void checkForAlerts(InventoryItem inventoryItem) {
        // Check for low stock
        if (inventoryItem.isReorderNeeded()) {
            logger.warn("Low stock alert for product: {} - Available: {}, Reorder Point: {}", 
                       inventoryItem.getProductId(), 
                       inventoryItem.getTotalAvailable(), 
                       inventoryItem.getReorderPoint());
        }

        // Check for expiry
        if (inventoryItem.isExpired()) {
            logger.warn("Expired product alert: {} - Expiry Date: {}", 
                       inventoryItem.getProductId(), 
                       inventoryItem.getAttributes().getExpiryDate());
        }

        // Check for approaching expiry (30 days)
        if (inventoryItem.getAttributes() != null && 
            inventoryItem.getAttributes().getExpiryDate() != null) {
            
            LocalDateTime warningDate = LocalDateTime.now().plusDays(30);
            if (inventoryItem.getAttributes().getExpiryDate().isBefore(warningDate)) {
                logger.warn("Product approaching expiry: {} - Expiry Date: {}", 
                           inventoryItem.getProductId(), 
                           inventoryItem.getAttributes().getExpiryDate());
            }
        }

        // Check for excessive damaged stock
        if (inventoryItem.getTotalDamaged() != null && inventoryItem.getTotalDamaged() > 0) {
            int totalStock = inventoryItem.getTotalAvailable() + 
                           inventoryItem.getTotalReserved() + 
                           inventoryItem.getTotalDamaged();
            
            if (totalStock > 0) {
                double damagedPercentage = (double) inventoryItem.getTotalDamaged() / totalStock * 100;
                if (damagedPercentage > 10) { // More than 10% damaged
                    logger.warn("High damaged stock alert for product: {} - Damaged: {} ({}%)", 
                               inventoryItem.getProductId(), 
                               inventoryItem.getTotalDamaged(), 
                               String.format("%.1f", damagedPercentage));
                }
            }
        }
    }

    private void addUpdateAuditEntry(InventoryItem inventoryItem) {
        InventoryItem.AuditLogEntry auditEntry = new InventoryItem.AuditLogEntry();
        auditEntry.setTimestamp(LocalDateTime.now());
        auditEntry.setReason("update");
        auditEntry.setActor("UpdateInventoryItemProcessor");
        auditEntry.setLocationId("ALL_LOCATIONS");
        auditEntry.setStockType("metadata");
        auditEntry.setDelta(0);
        auditEntry.setPreviousValue(null);
        auditEntry.setNewValue(null);
        auditEntry.setReferenceId(null);
        auditEntry.setNotes("Inventory item updated and validated");

        inventoryItem.getAuditLog().add(auditEntry);
    }
}
