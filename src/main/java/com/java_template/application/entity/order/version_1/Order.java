package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // UUID
    private String customerId; // UUID referencing User
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String currency;
    private String status; // PENDING_PAYMENT, PAID, PACKED, SHIPPED, DELIVERED, CANCELLED, REFUNDED
    private String paymentStatus; // NOT_ATTEMPTED, AUTHORIZED, CAPTURED, FAILED
    private String shippingAddress;
    private String billingAddress;
    private String createdAt; // ISO8601
    private String updatedAt; // ISO8601

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
        if (customerId == null || customerId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        for (OrderItem item : items) {
            if (item == null) return false;
            if (item.getProductId() == null || item.getProductId().isBlank()) return false;
            if (item.getQuantity() == null || item.getQuantity() <= 0) return false;
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) return false;
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (paymentStatus == null || paymentStatus.isBlank()) return false;
        if (shippingAddress == null || shippingAddress.isBlank()) return false;
        if (billingAddress == null || billingAddress.isBlank()) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private String productId; // UUID referencing Product
        private Integer quantity;
        private BigDecimal unitPrice;

        public OrderItem() {}
    }
}
