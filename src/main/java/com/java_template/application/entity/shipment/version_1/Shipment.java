package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment Entity - Shipment tracking for the OMS system
 * 
 * This entity represents a shipment with line items and tracking.
 * For demo purposes, there's one shipment per order.
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String shipmentId; // Business ID - unique identifier
    private String orderId; // Reference to order
    private String status; // "PICKING", "WAITING_TO_SEND", "SENT", "DELIVERED"
    private List<ShipmentLine> lines;
    
    // Optional fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return shipmentId != null && !shipmentId.trim().isEmpty() &&
               orderId != null && !orderId.trim().isEmpty() &&
               status != null && isValidStatus(status) &&
               lines != null && !lines.isEmpty();
    }

    /**
     * Validate shipment status values
     */
    private boolean isValidStatus(String status) {
        return "PICKING".equals(status) || 
               "WAITING_TO_SEND".equals(status) || 
               "SENT".equals(status) || 
               "DELIVERED".equals(status);
    }

    /**
     * Shipment line item representing a product in the shipment
     */
    @Data
    public static class ShipmentLine {
        private String sku; // Product SKU
        private Integer qtyOrdered; // Quantity originally ordered
        private Integer qtyPicked; // Quantity picked (initially 0)
        private Integer qtyShipped; // Quantity shipped (initially 0)
    }
}
