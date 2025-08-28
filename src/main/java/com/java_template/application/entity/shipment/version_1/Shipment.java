package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = "Shipment";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String shipmentNumber;
    private String orderId; // serialized UUID reference
    private String status; // use String for enums
    private String createdAt; // ISO-8601 timestamp
    private List<ShipmentItem> items;
    private Map<String, Object> trackingInfo;
    private String warehouseId; // serialized UUID reference

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
        // Basic string checks: ensure required string fields are present and not blank
        if (id == null || id.isBlank()) return false;
        if (shipmentNumber == null || shipmentNumber.isBlank()) return false;
        if (orderId == null || orderId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (warehouseId == null || warehouseId.isBlank()) return false;

        // Items must be present and each item must be valid
        if (items == null || items.isEmpty()) return false;
        for (ShipmentItem item : items) {
            if (item == null) return false;
            if (item.getProductId() == null || item.getProductId().isBlank()) return false;
            if (item.getQty() == null || item.getQty() <= 0) return false;
        }

        // trackingInfo can be null or empty, no strict validation required
        return true;
    }

    @Data
    public static class ShipmentItem {
        private String productId; // serialized UUID reference
        private Integer qty;
    }
}