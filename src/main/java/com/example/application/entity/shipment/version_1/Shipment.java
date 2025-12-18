package com.example.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment Entity - Single shipment per order for OMS
 * States: PICKING | WAITING_TO_SEND | SENT | DELIVERED
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = "Shipment";
    public static final Integer ENTITY_VERSION = 1;

    // Business ID
    private String shipmentId;

    // Reference to order
    private String orderId;

    // Status: PICKING | WAITING_TO_SEND | SENT | DELIVERED
    private String status;

    // Shipment lines
    private List<ShipmentLine> lines;

    // Metadata
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
    public boolean isValid(EntityMetadata metadata) {
        return shipmentId != null && !shipmentId.isBlank() && 
               orderId != null && !orderId.isBlank() && 
               status != null && !status.isBlank();
    }

    /**
     * Shipment line item
     */
    @Data
    public static class ShipmentLine {
        private String sku;
        private Integer qtyOrdered;
        private Integer qtyPicked;
        private Integer qtyShipped;
    }
}

