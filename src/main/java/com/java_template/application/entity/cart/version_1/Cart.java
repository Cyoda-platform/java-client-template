package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;

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

    // explicit getters and setters to avoid Lombok reliance
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public static class CartItem {
        private String productId; // serialized product id (sku)
        private Integer quantity;
        private Double unitPrice;

        public CartItem() {}

        public boolean isValid() {
            if (productId == null || productId.isBlank()) return false;
            if (quantity == null || quantity <= 0) return false;
            if (unitPrice == null || unitPrice < 0) return false;
            return true;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    }
}