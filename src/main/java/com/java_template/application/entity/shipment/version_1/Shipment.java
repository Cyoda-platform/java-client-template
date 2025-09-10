package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment Entity - Order shipments for OMS system
 * 
 * This entity represents shipments created from orders.
 * Entity state is managed by workflow: PICKING → WAITING_TO_SEND → SENT → DELIVERED
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core Fields
    private String shipmentId;             // Unique shipment identifier
    private String orderId;                // Associated order identifier
    private List<ShipmentLine> lines;      // Shipment line items

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
        return shipmentId != null && !shipmentId.trim().isEmpty() &&
               orderId != null && !orderId.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               lines.stream().allMatch(ShipmentLine::isValid);
    }

    /**
     * ShipmentLine Object - Individual line item in shipment
     */
    @Data
    public static class ShipmentLine {
        private String sku;                // Product SKU
        private Integer qtyOrdered;        // Quantity originally ordered
        private Integer qtyPicked;         // Quantity picked for shipment
        private Integer qtyShipped;        // Quantity actually shipped

        public boolean isValid() {
            return sku != null && !sku.trim().isEmpty() &&
                   qtyOrdered != null && qtyOrdered >= 0 &&
                   qtyPicked != null && qtyPicked >= 0 &&
                   qtyShipped != null && qtyShipped >= 0 &&
                   qtyPicked <= qtyOrdered &&
                   qtyShipped <= qtyPicked;
        }
    }
}
