package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = "Shipment"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String shipmentId;
    private String orderId; // foreign key reference (serialized UUID)
    private String status;
    private String createdAt;
    private String updatedAt;
    private List<ShipmentLine> lines;

    public Shipment() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (shipmentId == null || shipmentId.isBlank()) return false;
        if (orderId == null || orderId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (lines == null || lines.isEmpty()) return false;
        for (ShipmentLine line : lines) {
            if (line == null || !line.isValid()) return false;
        }
        return true;
    }

    @Data
    public static class ShipmentLine {
        private Integer qtyOrdered;
        private Integer qtyPicked;
        private Integer qtyShipped;
        private String sku;

        public boolean isValid() {
            if (sku == null || sku.isBlank()) return false;
            if (qtyOrdered == null || qtyOrdered < 0) return false;
            if (qtyPicked == null || qtyPicked < 0) return false;
            if (qtyShipped == null || qtyShipped < 0) return false;
            if (qtyPicked > qtyOrdered) return false;
            if (qtyShipped > qtyOrdered) return false;
            return true;
        }
    }
}