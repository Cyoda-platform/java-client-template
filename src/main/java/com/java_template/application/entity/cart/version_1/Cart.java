package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // serialized UUID / technical id
    private String userId; // serialized UUID, may be null for anonymous
    private String status; // use String for enums
    private String createdAt; // ISO timestamp as String
    private String updatedAt; // ISO timestamp as String
    private Double totalAmount;
    private List<CartItem> items;

    public Cart() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // id must be present
        if (id == null || id.isBlank()) return false;
        // createdAt and updatedAt should be present
        if (createdAt == null || createdAt.isBlank()) return false;
        if (updatedAt == null || updatedAt.isBlank()) return false;
        // status should be present
        if (status == null || status.isBlank()) return false;
        // totalAmount must be non-null and non-negative
        if (totalAmount == null || totalAmount < 0) return false;
        // items must be present and each item valid
        if (items == null || items.isEmpty()) return false;
        double sum = 0.0;
        for (CartItem item : items) {
            if (item == null || !item.isValid()) return false;
            sum += item.getUnitPrice() * item.getQuantity();
        }
        // allow small rounding differences
        if (Math.abs(sum - totalAmount) > 0.01) return false;
        return true;
    }

    @Data
    public static class CartItem {
        private String productId; // serialized product id (sku)
        private Integer quantity;
        private Double unitPrice;

        public boolean isValid() {
            if (productId == null || productId.isBlank()) return false;
            if (quantity == null || quantity <= 0) return false;
            if (unitPrice == null || unitPrice < 0) return false;
            return true;
        }
    }
}