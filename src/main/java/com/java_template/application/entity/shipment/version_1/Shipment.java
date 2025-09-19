package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment Entity - Single shipment per order in OMS
 * 
 * Represents a shipment containing items from an order.
 * Supports workflow states: PICKING → WAITING_TO_SEND → SENT → DELIVERED
 */
@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String shipmentId; // required, unique business identifier

    // Required core fields
    private String orderId; // required: reference to parent order
    private String status; // required: "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<ShipmentLine> lines; // required: shipment items with quantities

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
               status != null && isValidStatus(status) &&
               lines != null && !lines.isEmpty();
    }

    private boolean isValidStatus(String status) {
        return "PICKING".equals(status) || 
               "WAITING_TO_SEND".equals(status) || 
               "SENT".equals(status) || 
               "DELIVERED".equals(status);
    }

    /**
     * Shipment line item with picking and shipping quantities
     */
    @Data
    public static class ShipmentLine {
        private String sku; // product SKU
        private Integer qtyOrdered; // quantity originally ordered
        private Integer qtyPicked; // quantity picked for shipment
        private Integer qtyShipped; // quantity actually shipped
    }
}
