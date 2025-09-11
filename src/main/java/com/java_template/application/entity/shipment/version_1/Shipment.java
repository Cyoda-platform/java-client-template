package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment Entity - Represents a single shipment for an order (demo uses single shipment per order).
 * 
 * Entity state is managed by the workflow system:
 * - PICKING: Items are being picked from warehouse
 * - WAITING_TO_SEND: Items picked, waiting to ship
 * - SENT: Shipment has been sent
 * - DELIVERED: Shipment has been delivered
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String shipmentId;      // Required, unique
    private String orderId;         // Required: Associated order identifier
    private List<ShipmentLine> lines;   // Required: Shipment line items

    // Timestamps
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
        if (shipmentId == null || shipmentId.trim().isEmpty()) {
            return false;
        }
        if (orderId == null || orderId.trim().isEmpty()) {
            return false;
        }
        if (lines == null || lines.isEmpty()) {
            return false;
        }

        // Validate lines
        for (ShipmentLine line : lines) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                return false;
            }
            if (line.getQtyOrdered() == null || line.getQtyOrdered() < 0) {
                return false;
            }
            if (line.getQtyPicked() == null || line.getQtyPicked() < 0) {
                return false;
            }
            if (line.getQtyShipped() == null || line.getQtyShipped() < 0) {
                return false;
            }
            
            // Business rule validations
            if (line.getQtyPicked() > line.getQtyOrdered()) {
                return false;
            }
            if (line.getQtyShipped() > line.getQtyPicked()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Shipment line item with sku, qtyOrdered, qtyPicked, qtyShipped
     */
    @Data
    public static class ShipmentLine {
        private String sku;         // Product SKU
        private Integer qtyOrdered; // Quantity ordered
        private Integer qtyPicked;  // Quantity picked (must be <= qtyOrdered)
        private Integer qtyShipped; // Quantity shipped (must be <= qtyPicked)
    }
}
