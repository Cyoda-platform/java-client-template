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
 * Processor to check if inventory item needs reordering
 * Evaluates stock levels against reorder points
 */
@Component
public class ReorderCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReorderCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReorderCheckProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(InventoryItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid inventory item wrapper")
                .map(this::processReorderCheck)
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

    private EntityWithMetadata<InventoryItem> processReorderCheck(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {

        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();

        logger.debug("Processing reorder check for inventory item: {}", inventoryItem.getProductId());

        // Initialize collections if needed
        if (inventoryItem.getAuditLog() == null) {
            inventoryItem.setAuditLog(new ArrayList<>());
        }

        // Calculate total available stock
        int totalAvailable = inventoryItem.getTotalAvailableStock();
        
        // Check if reorder is needed
        boolean reorderNeeded = isReorderNeeded(inventoryItem, totalAvailable);
        
        if (reorderNeeded) {
            handleReorderNeeded(inventoryItem, totalAvailable);
        } else {
            logger.debug("No reorder needed for product: {} (Available: {}, Reorder Point: {})", 
                        inventoryItem.getProductId(), totalAvailable, inventoryItem.getReorderPoint());
        }

        // Update timestamps
        inventoryItem.setUpdatedAt(LocalDateTime.now());
        inventoryItem.setLastUpdatedBy("ReorderCheckProcessor");

        logger.info("Reorder check completed for inventory item: {} - Reorder needed: {}", 
                   inventoryItem.getProductId(), reorderNeeded);

        return entityWithMetadata;
    }

    private boolean isReorderNeeded(InventoryItem inventoryItem, int totalAvailable) {
        if (inventoryItem.getReorderPoint() == null) {
            logger.debug("No reorder point set for product: {}", inventoryItem.getProductId());
            return false;
        }
        
        return totalAvailable <= inventoryItem.getReorderPoint();
    }

    private void handleReorderNeeded(InventoryItem inventoryItem, int totalAvailable) {
        logger.warn("Reorder needed for product: {} - Available: {}, Reorder Point: {}", 
                   inventoryItem.getProductId(), totalAvailable, inventoryItem.getReorderPoint());

        // Add audit log entry for reorder alert
        addReorderAuditEntry(inventoryItem, totalAvailable);

        // In a real system, this would trigger:
        // 1. Purchase order creation
        // 2. Supplier notification
        // 3. Alert to procurement team
        // 4. Automatic reorder if configured
        
        simulateReorderAlert(inventoryItem, totalAvailable);
    }

    private void simulateReorderAlert(InventoryItem inventoryItem, int totalAvailable) {
        // Simulate sending reorder alert
        logger.info("REORDER ALERT: Product {} needs restocking. Current: {}, Reorder Point: {}, Suggested Quantity: {}", 
                   inventoryItem.getProductId(), 
                   totalAvailable, 
                   inventoryItem.getReorderPoint(),
                   inventoryItem.getReorderQuantity());

        // In real implementation, this would:
        // - Send email/notification to procurement team
        // - Create purchase order if auto-reorder is enabled
        // - Update external systems
        // - Log to monitoring/alerting systems
    }

    private void addReorderAuditEntry(InventoryItem inventoryItem, int totalAvailable) {
        InventoryItem.AuditLogEntry auditEntry = new InventoryItem.AuditLogEntry();
        auditEntry.setTimestamp(LocalDateTime.now());
        auditEntry.setReason("reorder_check");
        auditEntry.setActor("ReorderCheckProcessor");
        auditEntry.setLocationId("ALL_LOCATIONS");
        auditEntry.setStockType("available");
        auditEntry.setDelta(0); // No stock change, just alert
        auditEntry.setPreviousValue(totalAvailable);
        auditEntry.setNewValue(totalAvailable);
        auditEntry.setReferenceId(null);
        auditEntry.setNotes(String.format("Reorder alert triggered - Available: %d, Reorder Point: %d", 
                                         totalAvailable, inventoryItem.getReorderPoint()));

        inventoryItem.getAuditLog().add(auditEntry);
    }

    /**
     * Helper method to check if item is approaching expiry
     */
    private boolean isApproachingExpiry(InventoryItem inventoryItem) {
        if (inventoryItem.getAttributes() == null || 
            inventoryItem.getAttributes().getExpiryDate() == null) {
            return false;
        }

        LocalDateTime expiryDate = inventoryItem.getAttributes().getExpiryDate();
        LocalDateTime warningDate = LocalDateTime.now().plusDays(30); // 30 days warning
        
        return expiryDate.isBefore(warningDate);
    }

    /**
     * Helper method to calculate recommended reorder quantity
     */
    private int calculateRecommendedReorderQuantity(InventoryItem inventoryItem, int currentStock) {
        if (inventoryItem.getReorderQuantity() != null) {
            return inventoryItem.getReorderQuantity();
        }

        // Default calculation: order enough to reach 2x reorder point
        if (inventoryItem.getReorderPoint() != null) {
            int targetStock = inventoryItem.getReorderPoint() * 2;
            return Math.max(0, targetStock - currentStock);
        }

        // Fallback: order 100 units
        return 100;
    }
}
