package com.java_template.application.entity.shoppingcart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class ShoppingCart implements CyodaEntity {
    public static final String ENTITY_NAME = "ShoppingCart";
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on requirements example
    private String cartId; // technical id (serialized UUID or similar)
    private String customerUserId; // foreign key reference to User (serialized UUID)
    private String createdAt; // ISO-8601 timestamp string
    private String modifiedAt; // ISO-8601 timestamp string
    private List<Item> items;

    public ShoppingCart() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (cartId == null || cartId.isBlank()) return false;
        if (customerUserId == null || customerUserId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // modifiedAt may be optional in some flows, but if present ensure not blank
        if (modifiedAt != null && modifiedAt.isBlank()) return false;

        // items list must exist (can be empty) and each item must be valid if present
        if (items == null) return false;
        for (Item it : items) {
            if (it == null) return false;
            if (it.getProductSku() == null || it.getProductSku().isBlank()) return false;
            if (it.getQuantity() == null || it.getQuantity() <= 0) return false;
            if (it.getPriceAtAdd() == null || it.getPriceAtAdd() < 0.0) return false;
        }
        return true;
    }

    @Data
    public static class Item {
        private Double priceAtAdd;
        private String productSku;
        private Integer quantity;
    }
}