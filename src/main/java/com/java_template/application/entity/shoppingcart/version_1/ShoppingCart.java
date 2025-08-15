package com.java_template.application.entity.shoppingcart.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Data
public class ShoppingCart implements CyodaEntity {
    public static final String ENTITY_NAME = "ShoppingCart";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String cartId;
    private String customerId; // references User.id
    private List<CartItem> items;
    private String status; // OPEN, CHECKOUT_INITIATED, CHECKED_OUT, ABANDONED
    private String createdAt; // ISO8601
    private String updatedAt; // ISO8601
    private String expiresAt; // ISO8601

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
        if (cartId == null || cartId.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // items can be empty, but list must not be null
        if (items == null) return false;
        // validate items
        for (CartItem it : items) {
            if (it == null || it.getSku() == null || it.getSku().isBlank()) return false;
            if (it.getQuantity() == null || it.getQuantity() <= 0) return false;
            if (it.getUnitPrice() == null || it.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) return false;
        }
        return true;
    }

    @Data
    public static class CartItem {
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;

        public CartItem() {}
    }
}
