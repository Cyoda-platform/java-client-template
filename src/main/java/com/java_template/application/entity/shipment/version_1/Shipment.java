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

    // Technical identifier for the shipment (returned by POST)
    private String shipmentId;

    // Reference to the related order (serialized UUID or string id)
    private String orderId;

    // Current status (use String for enum-like values)
    private String status;

    // Timestamps as ISO-8601 strings
    private String createdAt;
    private String updatedAt;

    // Shipment lines
    private List<Line> lines;

    @Data
    public static class Line {
        private String sku;
        private Integer qtyOrdered;
        private Integer qtyPicked;
        private Integer qtyShipped;
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
        // shipmentId and orderId must be present
        if (shipmentId == null || shipmentId.isBlank()) return false;
        if (orderId == null || orderId.isBlank()) return false;

        // status and createdAt should be present
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // lines must be present and valid
        if (lines == null || lines.isEmpty()) return false;
        for (Line line : lines) {
            if (line == null) return false;
            if (line.getSku() == null || line.getSku().isBlank()) return false;
            if (line.getQtyOrdered() == null || line.getQtyOrdered() < 0) return false;
            if (line.getQtyPicked() != null && line.getQtyPicked() < 0) return false;
            if (line.getQtyShipped() != null && line.getQtyShipped() < 0) return false;
            // qtyPicked and qtyShipped should not exceed qtyOrdered if present
            if (line.getQtyPicked() != null && line.getQtyPicked() > line.getQtyOrdered()) return false;
            if (line.getQtyShipped() != null && line.getQtyShipped() > line.getQtyOrdered()) return false;
        }

        return true;
    }
}