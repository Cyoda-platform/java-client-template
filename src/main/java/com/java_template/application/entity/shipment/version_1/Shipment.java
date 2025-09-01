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

    // Entity fields based on requirements prototype
    private String shipmentId; // technical id
    private String orderId; // foreign key reference (serialized UUID)
    private String status; // use String for enum-like values
    private String createdAt; // ISO timestamp as String
    private String updatedAt; // ISO timestamp as String
    private List<Line> lines;

    @Data
    public static class Line {
        private String sku;
        private Integer qtyOrdered;
        private Integer qtyPicked;
        private Integer qtyShipped;

        public Line() {}
    }

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
        // Validate required string fields using isBlank checks (handle nulls)
        if (shipmentId == null || shipmentId.isBlank()) return false;
        if (orderId == null || orderId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Lines must be present and valid
        if (lines == null || lines.isEmpty()) return false;
        for (Line l : lines) {
            if (l == null) return false;
            if (l.getSku() == null || l.getSku().isBlank()) return false;
            if (l.getQtyOrdered() == null || l.getQtyOrdered() < 0) return false;
            if (l.getQtyPicked() == null || l.getQtyPicked() < 0) return false;
            if (l.getQtyShipped() == null || l.getQtyShipped() < 0) return false;
        }

        return true;
    }
}