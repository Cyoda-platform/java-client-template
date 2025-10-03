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
 * ABOUTME: AdjustStockProcessor handles manual stock adjustments with comprehensive audit logging
 * for inventory corrections, damage reports, and stock reconciliation.
 */
@Component
public class AdjustStockProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdjustStockProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AdjustStockProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processAdjustStock)
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

        // Validate stock locations exist
        if (inventoryItem.getStockByLocation() == null || inventoryItem.getStockByLocation().isEmpty()) {
            logger.error("No stock locations found for productId: {}", inventoryItem.getProductId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<InventoryItem> processAdjustStock(ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {
        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();
        logger.info("Processing stock adjustment for productId: {}", inventoryItem.getProductId());

        try {
            // Update stock levels and timestamps
            for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation().values()) {
                stock.setLastUpdated(LocalDateTime.now());
                
                // Validate no negative stock (prevent overselling)
                if (stock.hasNegativeStock()) {
                    logger.warn("Negative stock detected for productId: {}, location: {}, available: {}", 
                               inventoryItem.getProductId(), stock.getLocationId(), stock.getAvailable());
                    
                    // Auto-correct negative stock to zero
                    stock.setAvailable(0);
                    
                    // Add audit entry for auto-correction
                    addAuditEntry(inventoryItem, stock.getLocationId(), "auto_correction", 
                                "AdjustStockProcessor", "Auto-corrected negative stock to zero", null);
                }
            }

            // Check if reorder is needed after adjustment
            if (inventoryItem.needsReorder()) {
                logger.info("Reorder needed for productId: {}, current stock: {}, reorder point: {}", 
                           inventoryItem.getProductId(), inventoryItem.getTotalAvailableStock(), inventoryItem.getReorderPoint());
            }

            logger.info("Stock adjustment completed for productId: {}, total available: {}", 
                       inventoryItem.getProductId(), inventoryItem.getTotalAvailableStock());

            return new EntityWithMetadata<>(inventoryItem, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing stock adjustment for productId: {}", inventoryItem.getProductId(), e);
            throw new RuntimeException("Failed to adjust stock: " + e.getMessage(), e);
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
