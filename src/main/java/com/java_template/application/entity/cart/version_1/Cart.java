package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;

    private String cartId; // business id
    private String userId; // serialized UUID reference, nullable for guest
    private List<CartItem> items = new ArrayList<>();
    private Double subtotal;
    private Double shippingEstimate;
    private Double total;
    private String status; // OPEN CHECKOUT_INITIATED CHECKED_OUT ABANDONED

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
        if (cartId == null || cartId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (subtotal == null || subtotal < 0) return false;
        if (shippingEstimate == null || shippingEstimate < 0) return false;
        if (total == null || total < 0) return false;
        if (items == null) return false;
        for (CartItem it : items) {
            if (!it.isValid()) return false;
        }
        return true;
    }

    @Data
    public static class CartItem {
        private String productId;
        private String sku;
        private Integer qty;
        private Double priceAtAdd;

        public boolean isValid() {
            if (productId == null || productId.isBlank()) return false;
            if (sku == null || sku.isBlank()) return false;
            if (qty == null || qty < 0) return false;
            if (priceAtAdd == null || priceAtAdd < 0) return false;
            return true;
        }
    }
}
