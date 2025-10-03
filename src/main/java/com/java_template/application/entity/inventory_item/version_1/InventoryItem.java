package com.java_template.application.entity.inventory_item.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ABOUTME: InventoryItem entity represents product inventory with stock levels by location,
 * reorder management, and comprehensive audit logging for stock adjustments.
 */
@Data
public class InventoryItem implements CyodaEntity {
    public static final String ENTITY_NAME = InventoryItem.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String productId;
    
    // Core product fields
    private String sku;
    private String description;
    private ProductAttributes attributes;
    
    // Stock management by location
    private Map<String, StockByLocation> stockByLocation;
    
    // Reorder management
    private Integer reorderPoint;
    private Integer reorderQuantity;
    private String supplierRef;
    
    // Audit trail
    private List<AuditLogEntry> auditLog;

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
               stockByLocation != null && !stockByLocation.isEmpty() &&
               reorderPoint != null && reorderPoint >= 0 &&
               reorderQuantity != null && reorderQuantity > 0;
    }

    /**
     * Product attributes for categorization and tracking
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
        private String dimensions;
    }

    /**
     * Stock levels for a specific location
     */
    @Data
    public static class StockByLocation {
        private String locationId;
        private Integer available;
        private Integer reserved;
        private Integer damaged;
        private Integer inTransit;
        private LocalDateTime lastUpdated;

        public boolean isValid() {
            return locationId != null && !locationId.trim().isEmpty() &&
                   available != null && available >= 0 &&
                   reserved != null && reserved >= 0 &&
                   damaged != null && damaged >= 0;
        }

        public Integer getTotalStock() {
            return (available != null ? available : 0) +
                   (reserved != null ? reserved : 0) +
                   (damaged != null ? damaged : 0) +
                   (inTransit != null ? inTransit : 0);
        }

        public Integer getAvailableForSale() {
            return available != null ? available : 0;
        }

        public boolean hasNegativeStock() {
            return available != null && available < 0;
        }
    }

    /**
     * Audit log entry for tracking all stock changes
     */
    @Data
    public static class AuditLogEntry {
        private String entryId;
        private String reason; // sale, return, adjustment, damage, transfer, etc.
        private String actor; // user ID or system process
        private String locationId;
        private StockDelta delta;
        private LocalDateTime timestamp;
        private String notes;
        private String referenceId; // order ID, transfer ID, etc.

        public boolean isValid() {
            return entryId != null && !entryId.trim().isEmpty() &&
                   reason != null && !reason.trim().isEmpty() &&
                   actor != null && !actor.trim().isEmpty() &&
                   locationId != null && !locationId.trim().isEmpty() &&
                   delta != null && delta.isValid() &&
                   timestamp != null;
        }
    }

    /**
     * Stock change delta for audit logging
     */
    @Data
    public static class StockDelta {
        private Integer availableDelta;
        private Integer reservedDelta;
        private Integer damagedDelta;
        private Integer inTransitDelta;

        public boolean isValid() {
            return availableDelta != null || reservedDelta != null || 
                   damagedDelta != null || inTransitDelta != null;
        }

        public boolean hasChanges() {
            return (availableDelta != null && availableDelta != 0) ||
                   (reservedDelta != null && reservedDelta != 0) ||
                   (damagedDelta != null && damagedDelta != 0) ||
                   (inTransitDelta != null && inTransitDelta != 0);
        }
    }

    /**
     * Get total available stock across all locations
     */
    public Integer getTotalAvailableStock() {
        if (stockByLocation == null) return 0;
        return stockByLocation.values().stream()
                .mapToInt(stock -> stock.getAvailableForSale())
                .sum();
    }

    /**
     * Get total reserved stock across all locations
     */
    public Integer getTotalReservedStock() {
        if (stockByLocation == null) return 0;
        return stockByLocation.values().stream()
                .mapToInt(stock -> stock.reserved != null ? stock.reserved : 0)
                .sum();
    }

    /**
     * Check if reorder is needed based on total available stock
     */
    public boolean needsReorder() {
        return reorderPoint != null && getTotalAvailableStock() <= reorderPoint;
    }

    /**
     * Check if any location has negative stock (overselling)
     */
    public boolean hasNegativeStock() {
        if (stockByLocation == null) return false;
        return stockByLocation.values().stream()
                .anyMatch(StockByLocation::hasNegativeStock);
    }

    /**
     * Get stock for a specific location
     */
    public StockByLocation getStockForLocation(String locationId) {
        if (stockByLocation == null || locationId == null) return null;
        return stockByLocation.get(locationId);
    }
}
