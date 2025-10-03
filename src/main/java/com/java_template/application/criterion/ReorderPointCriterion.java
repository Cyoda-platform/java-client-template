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
 * Criterion to check if inventory item has reached reorder point
 * Determines when automatic reordering should be triggered
 */
@Component
public class ReorderPointCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(ReorderPointCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public ReorderPointCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntity(InventoryItem.class)
                .validate(this::isValidInventoryItem, "Invalid inventory item")
                .map(this::checkReorderPoint)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidInventoryItem(InventoryItem inventoryItem) {
        return inventoryItem != null && inventoryItem.getProductId() != null;
    }

    private boolean checkReorderPoint(CriterionSerializer.CriterionEntityResponseExecutionContext<InventoryItem> context) {
        InventoryItem inventoryItem = context.entity();
        
        logger.debug("Checking reorder point for inventory item: {}", inventoryItem.getProductId());

        try {
            // Check if reorder point is configured
            if (inventoryItem.getReorderPoint() == null) {
                logger.debug("No reorder point configured for product: {}", inventoryItem.getProductId());
                return false;
            }

            // Check if reorder quantity is configured
            if (inventoryItem.getReorderQuantity() == null || inventoryItem.getReorderQuantity() <= 0) {
                logger.debug("No valid reorder quantity configured for product: {}", inventoryItem.getProductId());
                return false;
            }

            // Check if supplier reference is available
            if (inventoryItem.getSupplierRef() == null || inventoryItem.getSupplierRef().trim().isEmpty()) {
                logger.debug("No supplier reference configured for product: {}", inventoryItem.getProductId());
                return false;
            }

            // Calculate current available stock
            int currentAvailable = inventoryItem.getTotalAvailableStock();
            int reorderPoint = inventoryItem.getReorderPoint();

            // Check if reorder is needed
            boolean reorderNeeded = currentAvailable <= reorderPoint;

            if (reorderNeeded) {
                logger.info("Reorder point reached for product: {} - Available: {}, Reorder Point: {}", 
                           inventoryItem.getProductId(), currentAvailable, reorderPoint);
                
                // Additional validations for reorder
                if (!validateReorderConditions(inventoryItem)) {
                    logger.warn("Reorder conditions not met for product: {}", inventoryItem.getProductId());
                    return false;
                }
                
                return true;
            } else {
                logger.debug("Reorder not needed for product: {} - Available: {}, Reorder Point: {}", 
                           inventoryItem.getProductId(), currentAvailable, reorderPoint);
                return false;
            }

        } catch (Exception e) {
            logger.error("Error during reorder point check for inventory item: {}", inventoryItem.getProductId(), e);
            return false;
        }
    }

    private boolean validateReorderConditions(InventoryItem inventoryItem) {
        // Check if product is not expired
        if (inventoryItem.isExpired()) {
            logger.debug("Product {} is expired, skipping reorder", inventoryItem.getProductId());
            return false;
        }

        // Check if product is not discontinued
        // In a real system, this would check a discontinued flag
        // For now, we'll assume all products are active

        // Check if there's excessive damaged stock
        if (hasDamagedStockIssues(inventoryItem)) {
            logger.warn("Product {} has damaged stock issues, review before reorder", inventoryItem.getProductId());
            // Still allow reorder but log warning
        }

        // Check if product is approaching expiry
        if (isApproachingExpiry(inventoryItem)) {
            logger.warn("Product {} is approaching expiry, consider reorder quantity", inventoryItem.getProductId());
            // Still allow reorder but log warning
        }

        // Validate reorder quantity is reasonable
        if (!isReorderQuantityReasonable(inventoryItem)) {
            logger.warn("Reorder quantity may be unreasonable for product: {}", inventoryItem.getProductId());
            return false;
        }

        return true;
    }

    private boolean hasDamagedStockIssues(InventoryItem inventoryItem) {
        if (inventoryItem.getTotalDamaged() == null || inventoryItem.getTotalDamaged() == 0) {
            return false;
        }

        int totalStock = inventoryItem.getTotalAvailableStock() + 
                        inventoryItem.getTotalReservedStock() + 
                        inventoryItem.getTotalDamaged();

        if (totalStock == 0) {
            return false;
        }

        double damagedPercentage = (double) inventoryItem.getTotalDamaged() / totalStock * 100;
        return damagedPercentage > 15; // More than 15% damaged
    }

    private boolean isApproachingExpiry(InventoryItem inventoryItem) {
        if (inventoryItem.getAttributes() == null || 
            inventoryItem.getAttributes().getExpiryDate() == null) {
            return false;
        }

        java.time.LocalDateTime expiryDate = inventoryItem.getAttributes().getExpiryDate();
        java.time.LocalDateTime warningDate = java.time.LocalDateTime.now().plusDays(30); // 30 days warning

        return expiryDate.isBefore(warningDate);
    }

    private boolean isReorderQuantityReasonable(InventoryItem inventoryItem) {
        int reorderQuantity = inventoryItem.getReorderQuantity();
        int reorderPoint = inventoryItem.getReorderPoint();

        // Reorder quantity should be at least equal to reorder point
        if (reorderQuantity < reorderPoint) {
            logger.debug("Reorder quantity ({}) is less than reorder point ({})", reorderQuantity, reorderPoint);
            return false;
        }

        // Reorder quantity shouldn't be excessively large (more than 100x reorder point)
        if (reorderQuantity > reorderPoint * 100) {
            logger.debug("Reorder quantity ({}) is excessively large compared to reorder point ({})", 
                        reorderQuantity, reorderPoint);
            return false;
        }

        return true;
    }

    /**
     * Calculate recommended reorder quantity based on current conditions
     */
    private int calculateRecommendedReorderQuantity(InventoryItem inventoryItem) {
        int currentAvailable = inventoryItem.getTotalAvailableStock();
        int reorderPoint = inventoryItem.getReorderPoint();
        int configuredReorderQuantity = inventoryItem.getReorderQuantity();

        // If approaching expiry, reduce reorder quantity
        if (isApproachingExpiry(inventoryItem)) {
            return Math.min(configuredReorderQuantity, reorderPoint * 2);
        }

        // If high demand (low current stock), increase reorder quantity
        if (currentAvailable < reorderPoint / 2) {
            return Math.max(configuredReorderQuantity, reorderPoint * 3);
        }

        return configuredReorderQuantity;
    }

    /**
     * Check if emergency reorder is needed (stock critically low)
     */
    private boolean isEmergencyReorderNeeded(InventoryItem inventoryItem) {
        if (inventoryItem.getReorderPoint() == null) {
            return false;
        }

        int currentAvailable = inventoryItem.getTotalAvailableStock();
        int emergencyThreshold = inventoryItem.getReorderPoint() / 2; // Half of reorder point

        return currentAvailable <= emergencyThreshold;
    }

    /**
     * Estimate days until stock out based on current consumption
     */
    private int estimateDaysUntilStockOut(InventoryItem inventoryItem) {
        // This would require historical consumption data
        // For now, return a simple estimate based on current stock and reorder point
        int currentAvailable = inventoryItem.getTotalAvailableStock();
        int reorderPoint = inventoryItem.getReorderPoint();

        if (reorderPoint <= 0) {
            return -1; // Cannot estimate
        }

        // Assume reorder point represents 7 days of consumption
        double dailyConsumption = (double) reorderPoint / 7;
        
        if (dailyConsumption <= 0) {
            return -1; // Cannot estimate
        }

        return (int) Math.ceil(currentAvailable / dailyConsumption);
    }
}
