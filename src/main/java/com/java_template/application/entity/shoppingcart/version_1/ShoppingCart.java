package com.java_template.application.entity.shoppingcart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShoppingCart implements CyodaEntity {
    public static final String ENTITY_NAME = "ShoppingCart";
    public static final Integer ENTITY_VERSION = 1;

    // Business fields
    private String id; // business cart id
    private String customerId; // reference to User.id (serialized UUID)
    private List<CartItem> items = new ArrayList<>();
    private String status; // ACTIVE, CHECKOUT_IN_PROGRESS, CHECKED_OUT, CANCELLED
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp

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
        if (id == null || id.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (items != null) {
            for (CartItem it : items) {
                if (it == null) return false;
                if (it.getProductId() == null || it.getProductId().isBlank()) return false;
                if (it.getQuantity() == null || it.getQuantity() <= 0) return false;
                if (it.getPriceAtAdd() == null || it.getPriceAtAdd().compareTo(BigDecimal.ZERO) < 0) return false;
            }
        }
        return true;
    }

    @Data
    public static class CartItem {
        private String productId; // reference to Product.id (serialized UUID)
        private Integer quantity;
        private BigDecimal priceAtAdd;

        public CartItem() {}
    }
}
