package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // UUID
    private String customerId; // UUID referencing User
    private List<CartItem> items;
    private BigDecimal totalAmount;
    private String currency;
    private String status; // OPEN, CHECKOUT_IN_PROGRESS, CHECKED_OUT, ABANDONED
    private String createdAt; // ISO8601
    private String updatedAt; // ISO8601

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
        if (customerId == null || customerId.isBlank()) return false;
        if (items == null) return false;
        for (CartItem item : items) {
            if (item == null) return false;
            if (item.getProductId() == null || item.getProductId().isBlank()) return false;
            if (item.getQuantity() == null || item.getQuantity() <= 0) return false;
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) return false;
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }

    @Data
    public static class CartItem {
        private String productId; // UUID referencing Product
        private Integer quantity;
        private BigDecimal unitPrice; // snapshot of price at add time

        public CartItem() {}
    }
}
