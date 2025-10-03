package com.java_template.application.entity.inventory_item.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * InventoryItem Entity for multi-channel retailer inventory tracking
 * 
 * Represents a product in inventory with location-based stock tracking,
 * reorder management, and comprehensive audit logging.
 */
@Data
public class InventoryItem implements CyodaEntity {
    public static final String ENTITY_NAME = InventoryItem.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Primary business identifier
    private String productId;
    
    // Product identification
    private String sku;
    private String description;
    
    // Product attributes
    private ProductAttributes attributes;
    
    // Stock tracking by location
    private List<StockByLocation> stockByLocation;
    
    // Reorder management
    private Integer reorderPoint;
    private Integer reorderQuantity;
    private String supplierRef;
    
    // Audit trail
    private List<AuditLogEntry> auditLog;
    
    // Calculated fields
    private Integer totalAvailable;
    private Integer totalReserved;
    private Integer totalDamaged;
    
    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastUpdatedBy;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return productId != null && !productId.trim().isEmpty() &&
               sku != null && !sku.trim().isEmpty() &&
               description != null && !description.trim().isEmpty();
    }

    /**
     * Product attributes including size, color, batch, expiry date
     */
    @Data
    public static class ProductAttributes {
        private String size;
        private String colour;
        private String batch;
        private LocalDateTime expiryDate;
        private String category;
        private String brand;
        private Double weight;
        private String weightUnit;
        private String dimensions;
        private Boolean isPerishable;
        private Boolean isHazardous;
    }

    /**
     * Stock levels by location
     */
    @Data
    public static class StockByLocation {
        private String locationId;
        private String locationName;
        private String locationType; // warehouse, store, distribution_center
        private Integer available;
        private Integer reserved;
        private Integer damaged;
        private Integer inTransit;
        private LocalDateTime lastStockCheck;
        private String lastCheckedBy;
        
        public boolean isValid() {
            return locationId != null && !locationId.trim().isEmpty() &&
                   available != null && available >= 0 &&
                   reserved != null && reserved >= 0 &&
                   damaged != null && damaged >= 0;
        }
        
        public Integer getTotalStock() {
            return (available != null ? available : 0) + 
                   (reserved != null ? reserved : 0) + 
                   (damaged != null ? damaged : 0);
        }
    }

    /**
     * Audit log entry for stock adjustments
     */
    @Data
    public static class AuditLogEntry {
        private LocalDateTime timestamp;
        private String reason; // sale, return, damage, adjustment, restock, transfer
        private String actor; // user_id or system_process
        private String locationId;
        private String stockType; // available, reserved, damaged
        private Integer delta; // positive for increase, negative for decrease
        private Integer previousValue;
        private Integer newValue;
        private String referenceId; // order_id, transfer_id, etc.
        private String notes;
        
        public boolean isValid() {
            return timestamp != null &&
                   reason != null && !reason.trim().isEmpty() &&
                   actor != null && !actor.trim().isEmpty() &&
                   locationId != null && !locationId.trim().isEmpty() &&
                   delta != null;
        }
    }

    /**
     * Helper method to get total available stock across all locations
     */
    public Integer getTotalAvailableStock() {
        if (stockByLocation == null) return 0;
        return stockByLocation.stream()
                .mapToInt(stock -> stock.getAvailable() != null ? stock.getAvailable() : 0)
                .sum();
    }

    /**
     * Helper method to get total reserved stock across all locations
     */
    public Integer getTotalReservedStock() {
        if (stockByLocation == null) return 0;
        return stockByLocation.stream()
                .mapToInt(stock -> stock.getReserved() != null ? stock.getReserved() : 0)
                .sum();
    }

    /**
     * Helper method to get available stock for a specific location
     */
    public Integer getAvailableStockForLocation(String locationId) {
        if (stockByLocation == null || locationId == null) return 0;
        return stockByLocation.stream()
                .filter(stock -> locationId.equals(stock.getLocationId()))
                .mapToInt(stock -> stock.getAvailable() != null ? stock.getAvailable() : 0)
                .sum();
    }

    /**
     * Helper method to check if reorder is needed
     */
    public boolean isReorderNeeded() {
        if (reorderPoint == null) return false;
        return getTotalAvailableStock() <= reorderPoint;
    }

    /**
     * Helper method to check if item is expired
     */
    public boolean isExpired() {
        if (attributes == null || attributes.getExpiryDate() == null) return false;
        return LocalDateTime.now().isAfter(attributes.getExpiryDate());
    }
}
