package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment Entity - Represents a shipment for order fulfillment (single shipment per order for demo)
 * 
 * Workflow States (managed by entity.meta.state):
 * - PICKING: Items being picked from warehouse
 * - WAITING_TO_SEND: Picked, ready for shipment
 * - SENT: Shipped to customer
 * - DELIVERED: Delivered to customer
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core Fields
    private String shipmentId; // required, unique - Shipment identifier
    private String orderId; // required - Reference to order
    private List<ShipmentLine> lines; // required - Shipment items
    private LocalDateTime createdAt; // auto-generated
    private LocalDateTime updatedAt; // auto-generated

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return shipmentId != null && !shipmentId.trim().isEmpty() &&
               orderId != null && !orderId.trim().isEmpty() &&
               lines != null && !lines.isEmpty();
    }

    /**
     * Nested class for shipment line items
     */
    @Data
    public static class ShipmentLine {
        private String sku; // Product SKU
        private Integer qtyOrdered; // Quantity ordered
        private Integer qtyPicked; // Quantity picked
        private Integer qtyShipped; // Quantity shipped
    }
}
