package com.java_template.application.criterion;

import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Criterion to validate stock levels and prevent negative stock
 * Ensures stock adjustments maintain data integrity
 */
@Component
public class StockValidationCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(StockValidationCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public StockValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntity(InventoryItem.class)
                .validate(this::isValidInventoryItem, "Invalid inventory item")
                .map(this::checkStockValidation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidInventoryItem(InventoryItem inventoryItem) {
        return inventoryItem != null && inventoryItem.getProductId() != null;
    }

    private boolean checkStockValidation(CriterionSerializer.CriterionEntityResponseExecutionContext<InventoryItem> context) {
        InventoryItem inventoryItem = context.entity();
        
        logger.debug("Validating stock for inventory item: {}", inventoryItem.getProductId());

        try {
            // Validate basic inventory item data
            if (!validateBasicData(inventoryItem)) {
                logger.warn("Basic data validation failed for inventory item: {}", inventoryItem.getProductId());
                return false;
            }

            // Validate stock levels
            if (!validateStockLevels(inventoryItem)) {
                logger.warn("Stock level validation failed for inventory item: {}", inventoryItem.getProductId());
                return false;
            }

            // Validate stock locations
            if (!validateStockLocations(inventoryItem)) {
                logger.warn("Stock location validation failed for inventory item: {}", inventoryItem.getProductId());
                return false;
            }

            // Check for negative stock prevention
            if (!validateNegativeStockPrevention(inventoryItem)) {
                logger.warn("Negative stock detected for inventory item: {}", inventoryItem.getProductId());
                return false;
            }

            // Validate reorder parameters
            if (!validateReorderParameters(inventoryItem)) {
                logger.warn("Reorder parameter validation failed for inventory item: {}", inventoryItem.getProductId());
                return false;
            }

            logger.info("Stock validation passed for inventory item: {}", inventoryItem.getProductId());
            return true;

        } catch (Exception e) {
            logger.error("Error during stock validation for inventory item: {}", inventoryItem.getProductId(), e);
            return false;
        }
    }

    private boolean validateBasicData(InventoryItem inventoryItem) {
        // Check product ID
        if (inventoryItem.getProductId() == null || inventoryItem.getProductId().trim().isEmpty()) {
            logger.debug("Product ID is missing");
            return false;
        }

        // Check SKU
        if (inventoryItem.getSku() == null || inventoryItem.getSku().trim().isEmpty()) {
            logger.debug("SKU is missing");
            return false;
        }

        // Check description
        if (inventoryItem.getDescription() == null || inventoryItem.getDescription().trim().isEmpty()) {
            logger.debug("Description is missing");
            return false;
        }

        return true;
    }

    private boolean validateStockLevels(InventoryItem inventoryItem) {
        if (inventoryItem.getStockByLocation() == null) {
            // No stock locations is valid for new items
            return true;
        }

        for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation()) {
            if (!validateStockLocation(stock)) {
                return false;
            }
        }

        return true;
    }

    private boolean validateStockLocation(InventoryItem.StockByLocation stock) {
        // Check location ID
        if (stock.getLocationId() == null || stock.getLocationId().trim().isEmpty()) {
            logger.debug("Location ID is missing");
            return false;
        }

        // Ensure stock values are not null
        if (stock.getAvailable() == null) stock.setAvailable(0);
        if (stock.getReserved() == null) stock.setReserved(0);
        if (stock.getDamaged() == null) stock.setDamaged(0);
        if (stock.getInTransit() == null) stock.setInTransit(0);

        // Check for negative values
        if (stock.getAvailable() < 0) {
            logger.debug("Available stock cannot be negative: {}", stock.getAvailable());
            return false;
        }

        if (stock.getReserved() < 0) {
            logger.debug("Reserved stock cannot be negative: {}", stock.getReserved());
            return false;
        }

        if (stock.getDamaged() < 0) {
            logger.debug("Damaged stock cannot be negative: {}", stock.getDamaged());
            return false;
        }

        if (stock.getInTransit() < 0) {
            logger.debug("In-transit stock cannot be negative: {}", stock.getInTransit());
            return false;
        }

        return true;
    }

    private boolean validateStockLocations(InventoryItem inventoryItem) {
        if (inventoryItem.getStockByLocation() == null || inventoryItem.getStockByLocation().isEmpty()) {
            return true; // No locations is valid
        }

        // Check for duplicate location IDs
        long uniqueLocationCount = inventoryItem.getStockByLocation().stream()
                .map(InventoryItem.StockByLocation::getLocationId)
                .distinct()
                .count();

        if (uniqueLocationCount != inventoryItem.getStockByLocation().size()) {
            logger.debug("Duplicate location IDs found");
            return false;
        }

        // Validate location types
        for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation()) {
            if (!isValidLocationType(stock.getLocationType())) {
                logger.debug("Invalid location type: {}", stock.getLocationType());
                return false;
            }
        }

        return true;
    }

    private boolean isValidLocationType(String locationType) {
        if (locationType == null) {
            return true; // Optional field
        }
        
        return "warehouse".equals(locationType) ||
               "store".equals(locationType) ||
               "distribution_center".equals(locationType) ||
               "supplier".equals(locationType);
    }

    private boolean validateNegativeStockPrevention(InventoryItem inventoryItem) {
        // Calculate total stock levels
        int totalAvailable = inventoryItem.getTotalAvailableStock();
        int totalReserved = inventoryItem.getTotalReservedStock();

        // Check if total available is sufficient for total reserved
        // This shouldn't happen in normal operations
        if (totalAvailable < 0) {
            logger.debug("Total available stock is negative: {}", totalAvailable);
            return false;
        }

        if (totalReserved < 0) {
            logger.debug("Total reserved stock is negative: {}", totalReserved);
            return false;
        }

        // Validate that reserved stock doesn't exceed total stock
        int totalStock = totalAvailable + totalReserved;
        if (inventoryItem.getTotalDamaged() != null) {
            totalStock += inventoryItem.getTotalDamaged();
        }

        if (totalReserved > totalStock) {
            logger.debug("Reserved stock ({}) exceeds total stock ({})", totalReserved, totalStock);
            return false;
        }

        return true;
    }

    private boolean validateReorderParameters(InventoryItem inventoryItem) {
        // Reorder point validation
        if (inventoryItem.getReorderPoint() != null && inventoryItem.getReorderPoint() < 0) {
            logger.debug("Reorder point cannot be negative: {}", inventoryItem.getReorderPoint());
            return false;
        }

        // Reorder quantity validation
        if (inventoryItem.getReorderQuantity() != null && inventoryItem.getReorderQuantity() <= 0) {
            logger.debug("Reorder quantity must be positive: {}", inventoryItem.getReorderQuantity());
            return false;
        }

        // If reorder point is set, reorder quantity should also be set
        if (inventoryItem.getReorderPoint() != null && inventoryItem.getReorderPoint() > 0 &&
            (inventoryItem.getReorderQuantity() == null || inventoryItem.getReorderQuantity() <= 0)) {
            logger.debug("Reorder quantity should be set when reorder point is specified");
            return false;
        }

        return true;
    }

    /**
     * Additional validation for expiry dates
     */
    private boolean validateExpiryDate(InventoryItem inventoryItem) {
        if (inventoryItem.getAttributes() == null || 
            inventoryItem.getAttributes().getExpiryDate() == null) {
            return true; // No expiry date is valid
        }

        java.time.LocalDateTime expiryDate = inventoryItem.getAttributes().getExpiryDate();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // Warn if product is already expired
        if (expiryDate.isBefore(now)) {
            logger.warn("Product {} is expired: {}", inventoryItem.getProductId(), expiryDate);
            // Still return true as expired products can exist in inventory
        }

        return true;
    }

    /**
     * Validate audit log integrity
     */
    private boolean validateAuditLog(InventoryItem inventoryItem) {
        if (inventoryItem.getAuditLog() == null) {
            return true; // No audit log is valid for new items
        }

        for (InventoryItem.AuditLogEntry entry : inventoryItem.getAuditLog()) {
            if (!entry.isValid()) {
                logger.debug("Invalid audit log entry found");
                return false;
            }
        }

        return true;
    }
}
