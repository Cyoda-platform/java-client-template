package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment Entity - Shipment tracking for OMS
 * 
 * Represents a single shipment for an order with line items,
 * quantities, and status tracking. Demo uses single shipment per order.
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String shipmentId; // required, unique business identifier

    // Required core fields
    private String orderId; // reference to order being shipped
    private String status; // "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<ShipmentLine> lines; // shipment line items

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
               status != null && !status.trim().isEmpty() &&
               lines != null && !lines.isEmpty();
    }

    /**
     * Shipment line item with quantities for ordered, picked, and shipped
     */
    @Data
    public static class ShipmentLine {
        private String sku; // product SKU
        private Integer qtyOrdered; // quantity originally ordered
        private Integer qtyPicked; // quantity picked for shipment
        private Integer qtyShipped; // quantity actually shipped
    }
}
