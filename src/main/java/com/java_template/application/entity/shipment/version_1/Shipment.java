package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment Entity - Physical fulfillment of an order
 * 
 * Represents the shipment tracking for order fulfillment.
 * State is managed via entity metadata (PICKING → WAITING_TO_SEND → SENT → DELIVERED).
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
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
               lines != null && !lines.isEmpty();
    }

    /**
     * Shipment line item with picking and shipping quantities
     */
    @Data
    public static class ShipmentLine {
        private String sku;                // Product SKU
        private Integer qtyOrdered;        // Quantity ordered
        private Integer qtyPicked;         // Quantity picked from warehouse
        private Integer qtyShipped;        // Quantity shipped to customer
    }
}
