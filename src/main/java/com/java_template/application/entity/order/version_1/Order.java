package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String userId; // serialized UUID
    private String billingAddressId; // serialized UUID
    private String shippingAddressId; // serialized UUID
    private String currency;
    private String status;
    private String paymentStatus;
    private Double total;
    private String createdAt; // ISO timestamp
    private List<OrderItem> items;

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
        // Validate essential string fields (use isBlank checks)
        if (id == null || id.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (billingAddressId == null || billingAddressId.isBlank()) return false;
        if (shippingAddressId == null || shippingAddressId.isBlank()) return false;
        if (currency == null || currency.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (paymentStatus == null || paymentStatus.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Validate items
        if (items == null || items.isEmpty()) return false;
        double sum = 0.0;
        for (OrderItem item : items) {
            if (item == null || !item.isValid()) return false;
            sum += item.getUnitPrice() * item.getQuantity();
        }

        // Validate total
        if (total == null) return false;
        // Allow small rounding differences
        if (Math.abs(total - sum) > 0.01) return false;

        return true;
    }

    @Data
    public static class OrderItem {
        private String name;
        private String productId; // serialized UUID
        private Integer quantity;
        private Double unitPrice;

        public boolean isValid() {
            if (name == null || name.isBlank()) return false;
            if (productId == null || productId.isBlank()) return false;
            if (quantity == null || quantity <= 0) return false;
            if (unitPrice == null || unitPrice < 0.0) return false;
            return true;
        }
    }
}