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
 * Criterion to check stock availability for reservations and transfers
 * Prevents overselling and ensures sufficient stock for operations
 */
@Component
public class StockAvailabilityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(StockAvailabilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public StockAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntity(InventoryItem.class)
                .validate(this::isValidInventoryItem, "Invalid inventory item")
                .map(this::checkStockAvailability)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidInventoryItem(InventoryItem inventoryItem) {
        return inventoryItem != null && inventoryItem.getProductId() != null;
    }

    private boolean checkStockAvailability(CriterionSerializer.CriterionEntityResponseExecutionContext<InventoryItem> context) {
        InventoryItem inventoryItem = context.entity();
        
        logger.debug("Checking stock availability for inventory item: {}", inventoryItem.getProductId());

        try {
            // Check if product is expired
            if (inventoryItem.isExpired()) {
                logger.debug("Product {} is expired and not available", inventoryItem.getProductId());
                return false;
            }

            // Check total available stock
            int totalAvailable = inventoryItem.getTotalAvailableStock();
            if (totalAvailable <= 0) {
                logger.debug("No available stock for product: {}", inventoryItem.getProductId());
                return false;
            }

            // Check if any location has available stock
            if (!hasAvailableStockInAnyLocation(inventoryItem)) {
                logger.debug("No location has available stock for product: {}", inventoryItem.getProductId());
                return false;
            }

            // Check for quality issues
            if (!validateStockQuality(inventoryItem)) {
                logger.debug("Stock quality issues for product: {}", inventoryItem.getProductId());
                return false;
            }

            // Check if product is approaching expiry
            if (isApproachingExpiry(inventoryItem)) {
                logger.warn("Product {} is approaching expiry but still available", inventoryItem.getProductId());
                // Still return true but log warning
            }

            logger.info("Stock availability check passed for inventory item: {} - Available: {}", 
                       inventoryItem.getProductId(), totalAvailable);
            return true;

        } catch (Exception e) {
            logger.error("Error during stock availability check for inventory item: {}", inventoryItem.getProductId(), e);
            return false;
        }
    }

    private boolean hasAvailableStockInAnyLocation(InventoryItem inventoryItem) {
        if (inventoryItem.getStockByLocation() == null) {
            return false;
        }

        return inventoryItem.getStockByLocation().stream()
                .anyMatch(stock -> stock.getAvailable() != null && stock.getAvailable() > 0);
    }

    private boolean validateStockQuality(InventoryItem inventoryItem) {
        if (inventoryItem.getStockByLocation() == null) {
            return true;
        }

        // Check for excessive damaged stock ratio
        for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation()) {
            int totalStockAtLocation = (stock.getAvailable() != null ? stock.getAvailable() : 0) +
                                     (stock.getReserved() != null ? stock.getReserved() : 0) +
                                     (stock.getDamaged() != null ? stock.getDamaged() : 0);

            if (totalStockAtLocation > 0 && stock.getDamaged() != null) {
                double damagedRatio = (double) stock.getDamaged() / totalStockAtLocation;
                if (damagedRatio > 0.5) { // More than 50% damaged at this location
                    logger.warn("High damaged stock ratio at location {} for product {}: {}%", 
                               stock.getLocationId(), inventoryItem.getProductId(), 
                               String.format("%.1f", damagedRatio * 100));
                    // Continue checking other locations
                }
            }
        }

        return true;
    }

    private boolean isApproachingExpiry(InventoryItem inventoryItem) {
        if (inventoryItem.getAttributes() == null || 
            inventoryItem.getAttributes().getExpiryDate() == null) {
            return false;
        }

        java.time.LocalDateTime expiryDate = inventoryItem.getAttributes().getExpiryDate();
        java.time.LocalDateTime warningDate = java.time.LocalDateTime.now().plusDays(7); // 7 days warning

        return expiryDate.isBefore(warningDate);
    }

    /**
     * Check if sufficient stock is available for a specific quantity
     */
    public boolean hasSufficientStock(InventoryItem inventoryItem, int requiredQuantity) {
        if (requiredQuantity <= 0) {
            return true; // No stock needed
        }

        int totalAvailable = inventoryItem.getTotalAvailableStock();
        return totalAvailable >= requiredQuantity;
    }

    /**
     * Check if a specific location has sufficient stock
     */
    public boolean hasLocationWithSufficientStock(InventoryItem inventoryItem, int requiredQuantity) {
        if (inventoryItem.getStockByLocation() == null) {
            return false;
        }

        return inventoryItem.getStockByLocation().stream()
                .anyMatch(stock -> stock.getAvailable() != null && 
                                 stock.getAvailable() >= requiredQuantity);
    }

    /**
     * Get the maximum quantity available for immediate fulfillment
     */
    public int getMaximumAvailableQuantity(InventoryItem inventoryItem) {
        return inventoryItem.getTotalAvailableStock();
    }

    /**
     * Check if product is suitable for sale (not expired, not damaged)
     */
    private boolean isSuitableForSale(InventoryItem inventoryItem) {
        // Check if expired
        if (inventoryItem.isExpired()) {
            return false;
        }

        // Check if product attributes indicate it's not suitable for sale
        if (inventoryItem.getAttributes() != null) {
            // Check if it's a hazardous material that requires special handling
            if (Boolean.TRUE.equals(inventoryItem.getAttributes().getIsHazardous())) {
                logger.debug("Product {} is hazardous and may require special handling", 
                           inventoryItem.getProductId());
                // Still allow sale but log for special handling
            }
        }

        return true;
    }

    /**
     * Calculate lead time for additional stock if current stock is insufficient
     */
    private int calculateLeadTime(InventoryItem inventoryItem) {
        // If reorder information is available, estimate lead time
        if (inventoryItem.getSupplierRef() != null && 
            inventoryItem.getReorderQuantity() != null && 
            inventoryItem.getReorderQuantity() > 0) {
            return 7; // Assume 7 days lead time
        }

        return -1; // No reorder possible
    }
}
