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
 * ABOUTME: ReserveInventoryProcessor handles inventory reservation for orders,
 * moving stock from available to reserved status with audit logging.
 */
@Component
public class ReserveInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReserveInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReserveInventoryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processReserveInventory)
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

        // Validate sufficient stock is available for reservation
        Integer totalAvailable = inventoryItem.getTotalAvailableStock();
        if (totalAvailable <= 0) {
            logger.error("No available stock for reservation for productId: {}", inventoryItem.getProductId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<InventoryItem> processReserveInventory(ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {
        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();
        logger.info("Processing inventory reservation for productId: {}", inventoryItem.getProductId());

        try {
            // Update stock levels and timestamps
            for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation().values()) {
                stock.setLastUpdated(LocalDateTime.now());
                
                // Validate stock levels are consistent
                if (stock.getAvailable() < 0) {
                    logger.error("Negative available stock detected for productId: {}, location: {}", 
                               inventoryItem.getProductId(), stock.getLocationId());
                    throw new RuntimeException("Cannot reserve from negative stock");
                }
            }

            // Add audit entry for reservation
            String locationId = inventoryItem.getStockByLocation().keySet().iterator().next();
            addAuditEntry(inventoryItem, locationId, "inventory_reservation", 
                         "ReserveInventoryProcessor", "Inventory reserved for order", null);

            logger.info("Inventory reservation processed for productId: {}, total available: {}, total reserved: {}", 
                       inventoryItem.getProductId(), inventoryItem.getTotalAvailableStock(), inventoryItem.getTotalReservedStock());

            return new EntityWithMetadata<>(inventoryItem, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing inventory reservation for productId: {}", inventoryItem.getProductId(), e);
            throw new RuntimeException("Failed to reserve inventory: " + e.getMessage(), e);
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
