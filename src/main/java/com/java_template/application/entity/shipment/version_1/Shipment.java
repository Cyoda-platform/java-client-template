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
    private String orderId; // foreign key reference as serialized UUID
    private String carrier;
    private String trackingNumber;
    private String status;
    private String createdAt;
    private String updatedAt;
    private List<Line> lines;

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
        // Basic required field checks
        if (shipmentId == null || shipmentId.isBlank()) return false;
        if (orderId == null || orderId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // lines must be present and each line must be valid
        if (lines == null || lines.isEmpty()) return false;
        for (Line l : lines) {
            if (l == null) return false;
            if (l.getSku() == null || l.getSku().isBlank()) return false;
            if (l.getQtyOrdered() == null || l.getQtyOrdered() < 0) return false;
            if (l.getQtyPicked() != null && l.getQtyPicked() < 0) return false;
            if (l.getQtyShipped() != null && l.getQtyShipped() < 0) return false;
        }
        return true;
    }

    @Data
    public static class Line {
        private String sku;
        private Integer qtyOrdered;
        private Integer qtyPicked;
        private Integer qtyShipped;
    }
}