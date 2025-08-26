package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String orderId; // technical/business id (serialized UUID or string)
    private String createdAt; // ISO timestamp
    private String customerUserId; // foreign key reference as serialized UUID (string)
    private List<Item> items;
    private String status; // use String for enums
    private Double subtotal;
    private Double total;

    public Order() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic required string checks
        if (orderId == null || orderId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (customerUserId == null || customerUserId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Items must be present
        if (items == null || items.isEmpty()) return false;

        // Validate each item and compute subtotal candidate
        double computedSubtotal = 0.0;
        for (Item it : items) {
            if (it == null) return false;
            if (it.getProductSku() == null || it.getProductSku().isBlank()) return false;
            if (it.getQuantity() == null || it.getQuantity() <= 0) return false;
            if (it.getUnitPrice() == null || it.getUnitPrice() < 0.0) return false;
            computedSubtotal += it.getQuantity() * it.getUnitPrice();
        }

        // Subtotal must be present and match computed subtotal within tolerance
        if (subtotal == null) return false;
        if (Math.abs(subtotal - computedSubtotal) > 0.01) return false;

        // Total must be present, non-negative, and at least subtotal (allowing small tolerance)
        if (total == null) return false;
        if (total < 0.0) return false;
        if (total + 0.01 < subtotal) return false;

        return true;
    }

    @Data
    public static class Item {
        private String productSku;
        private Integer quantity;
        private Double unitPrice;
    }
}