package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Shipment entity representing shipment tracking with line items
 * for order fulfillment and delivery status management.
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String shipmentId; // Business ID - required, unique
    private String orderId; // Reference to order - required
    private String status; // "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<ShipmentLine> lines; // required

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
    public boolean isValid(org.cyoda.cloud.api.event.common.EntityMetadata metadata) {
        return shipmentId != null && !shipmentId.trim().isEmpty() &&
               orderId != null && !orderId.trim().isEmpty() &&
               status != null && !status.trim().isEmpty() &&
               lines != null && !lines.isEmpty();
    }

    /**
     * Shipment line item representing quantities for a product
     */
    @Data
    public static class ShipmentLine {
        private String sku; // Product SKU
        private Integer qtyOrdered; // Quantity originally ordered
        private Integer qtyPicked; // Quantity picked for shipment
        private Integer qtyShipped; // Quantity actually shipped
    }
}
