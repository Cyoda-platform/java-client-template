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
 * ABOUTME: RestockInventoryProcessor handles inventory restocking operations,
 * adding new stock to available inventory with comprehensive audit logging.
 */
@Component
public class RestockInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RestockInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RestockInventoryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processRestockInventory)
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

        // Validate reorder information exists
        if (inventoryItem.getReorderQuantity() == null || inventoryItem.getReorderQuantity() <= 0) {
            logger.error("Invalid reorder quantity for productId: {}", inventoryItem.getProductId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<InventoryItem> processRestockInventory(ProcessorSerializer.ProcessorEntityResponseExecutionContext<InventoryItem> context) {
        EntityWithMetadata<InventoryItem> entityWithMetadata = context.entityResponse();
        InventoryItem inventoryItem = entityWithMetadata.entity();
        logger.info("Processing inventory restock for productId: {}", inventoryItem.getProductId());

        try {
            Integer restockQuantity = inventoryItem.getReorderQuantity();
            
            // Add stock to the first available location (simplified logic)
            if (inventoryItem.getStockByLocation() != null && !inventoryItem.getStockByLocation().isEmpty()) {
                String locationId = inventoryItem.getStockByLocation().keySet().iterator().next();
                InventoryItem.StockByLocation stock = inventoryItem.getStockByLocation().get(locationId);
                
                // Add restock quantity to available stock
                Integer currentAvailable = stock.getAvailable() != null ? stock.getAvailable() : 0;
                stock.setAvailable(currentAvailable + restockQuantity);
                stock.setLastUpdated(LocalDateTime.now());
                
                // Add audit entry for restock
                InventoryItem.StockDelta delta = new InventoryItem.StockDelta();
                delta.setAvailableDelta(restockQuantity);
                
                addAuditEntry(inventoryItem, locationId, "restock", 
                             "RestockInventoryProcessor", 
                             "Inventory restocked with " + restockQuantity + " units", delta);
                
                logger.info("Restocked {} units for productId: {} at location: {}, new available: {}", 
                           restockQuantity, inventoryItem.getProductId(), locationId, stock.getAvailable());
            }

            // Check if item still needs reorder after restocking
            if (inventoryItem.needsReorder()) {
                logger.warn("Product still needs reorder after restocking for productId: {}, current stock: {}, reorder point: {}", 
                           inventoryItem.getProductId(), inventoryItem.getTotalAvailableStock(), inventoryItem.getReorderPoint());
            } else {
                logger.info("Reorder requirement satisfied for productId: {}, current stock: {}", 
                           inventoryItem.getProductId(), inventoryItem.getTotalAvailableStock());
            }

            return new EntityWithMetadata<>(inventoryItem, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing inventory restock for productId: {}", inventoryItem.getProductId(), e);
            throw new RuntimeException("Failed to restock inventory: " + e.getMessage(), e);
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
