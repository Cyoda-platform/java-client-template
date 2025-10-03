package com.java_template.application.processor;

import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
import java.util.UUID;

/**
 * ABOUTME: UpdateInventoryProcessor handles general inventory updates including
 * product information changes, reorder point adjustments, and supplier updates.
 */
@Component
public class UpdateInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UpdateInventoryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(InventoryItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid inventory item entity wrapper")
                .map(this::processUpdateInventory)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<InventoryItem> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or InventoryItem entity is null");
            return false;
        }

        InventoryItem inventoryItem = entityWithMetadata.entity();
        
        // Validate inventory item has required fields
        if (!inventoryItem.isValid()) {
            logger.error("InventoryItem validation failed for productId: {}", inventoryItem.getProductId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<InventoryItem> processUpdateInventory(ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {
        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();
        logger.info("Processing inventory update for productId: {}", inventoryItem.getProductId());

        try {
            // Update stock timestamps
            if (inventoryItem.getStockByLocation() != null) {
                for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation().values()) {
                    stock.setLastUpdated(LocalDateTime.now());
                }
            }

            // Validate reorder points are reasonable
            if (inventoryItem.getReorderPoint() != null && inventoryItem.getReorderPoint() < 0) {
                logger.warn("Negative reorder point detected for productId: {}, setting to 0", inventoryItem.getProductId());
                inventoryItem.setReorderPoint(0);
            }

            if (inventoryItem.getReorderQuantity() != null && inventoryItem.getReorderQuantity() <= 0) {
                logger.warn("Invalid reorder quantity detected for productId: {}, setting to 1", inventoryItem.getProductId());
                inventoryItem.setReorderQuantity(1);
            }

            // Check for expiry date warnings
            if (inventoryItem.getAttributes() != null && inventoryItem.getAttributes().getExpiryDate() != null) {
                LocalDateTime expiryDate = inventoryItem.getAttributes().getExpiryDate();
                LocalDateTime warningDate = LocalDateTime.now().plusDays(30); // 30 days warning
                
                if (expiryDate.isBefore(warningDate)) {
                    logger.warn("Product nearing expiry for productId: {}, expiry date: {}", 
                               inventoryItem.getProductId(), expiryDate);
                    
                    // Add audit entry for expiry warning
                    addAuditEntry(inventoryItem, "SYSTEM", "expiry_warning", 
                                "UpdateInventoryProcessor", "Product nearing expiry date", null);
                }
            }

            // Add general update audit entry
            addAuditEntry(inventoryItem, "SYSTEM", "inventory_update", 
                         "UpdateInventoryProcessor", "Inventory item updated", null);

            logger.info("Inventory update completed for productId: {}", inventoryItem.getProductId());

            return new EntityWithMetadata<>(inventoryItem, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing inventory update for productId: {}", inventoryItem.getProductId(), e);
            throw new RuntimeException("Failed to update inventory: " + e.getMessage(), e);
        }
    }

    private void addAuditEntry(InventoryItem inventoryItem, String locationId, String reason, 
                              String actor, String notes, InventoryItem.StockDelta delta) {
        InventoryItem.AuditLogEntry auditEntry = new InventoryItem.AuditLogEntry();
        auditEntry.setEntryId(UUID.randomUUID().toString());
        auditEntry.setReason(reason);
        auditEntry.setActor(actor);
        auditEntry.setLocationId(locationId);
        auditEntry.setTimestamp(LocalDateTime.now());
        auditEntry.setNotes(notes);
        auditEntry.setDelta(delta);

        if (inventoryItem.getAuditLog() == null) {
            inventoryItem.setAuditLog(new ArrayList<>());
        }
        inventoryItem.getAuditLog().add(auditEntry);
    }
}
