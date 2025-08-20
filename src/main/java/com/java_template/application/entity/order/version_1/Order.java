package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Order fields
    private String orderId; // business id
    private String userId; // serialized UUID reference
    private String cartId; // serialized UUID reference
    private List<OrderItem> items = new ArrayList<>();
    private Double subtotal;
    private Double shipping;
    private Double total;
    private String shippingAddressId;
    private String billingAddressId;
    private String paymentStatus; // e.g. PAYMENT_PENDING, PAID
    private String fulfillmentStatus; // e.g. CONFIRMED, SHIPPED, DELIVERED, CANCELLED, REFUNDED

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
        if (orderId == null || orderId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        if (subtotal == null || subtotal < 0) return false;
        if (shipping == null || shipping < 0) return false;
        if (total == null || total < 0) return false;
        if (shippingAddressId == null || shippingAddressId.isBlank()) return false;
        if (billingAddressId == null || billingAddressId.isBlank()) return false;
        if (paymentStatus == null || paymentStatus.isBlank()) return false;
        if (fulfillmentStatus == null || fulfillmentStatus.isBlank()) return false;
        for (OrderItem it : items) {
            if (!it.isValid()) return false;
        }
        return true;
    }

    @Data
    public static class OrderItem {
        private String productId;
        private Integer qty;
        private Double price;

        public boolean isValid() {
            if (productId == null || productId.isBlank()) return false;
            if (qty == null || qty < 0) return false;
            if (price == null || price < 0) return false;
            return true;
        }
    }
}
