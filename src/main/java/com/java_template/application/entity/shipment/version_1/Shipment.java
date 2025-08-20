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

    private String shipmentId; // carrier or internal id
    private String orderId; // serialized UUID reference to Order
    private List<ShipmentItem> items;
    private String trackingNumber;
    private String carrier;
    private String status; // workflow-driven state
    private String createdAt; // ISO timestamp

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
        if (orderId == null || orderId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        if (carrier == null || carrier.isBlank()) return false;
        return true;
    }

    @Data
    public static class ShipmentItem {
        private String sku;
        private Integer quantity;

        public boolean isValid() {
            if (sku == null || sku.isBlank()) return false;
            if (quantity == null || quantity <= 0) return false;
            return true;
        }
    }
}
