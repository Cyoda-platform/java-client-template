package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment entity for shipping management in OMS
 * Represents a single shipment for an order with line items and status tracking
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String shipmentId;
    
    // Required fields
    private String orderId; // Reference to the order being shipped
    private String status; // "PICKING", "WAITING_TO_SEND", "SENT", "DELIVERED"
    private List<ShipmentLine> lines;
    
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
     * Shipment line item representing quantities for a product
     */
    @Data
    public static class ShipmentLine {
        private String sku;
        private Integer qtyOrdered;
        private Integer qtyPicked;
        private Integer qtyShipped;
    }
}
