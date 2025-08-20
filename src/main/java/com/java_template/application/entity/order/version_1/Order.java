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

    private String orderId;
    private String userId;
    private String cartId;
    private List<OrderItem> items;
    private Double subtotal;
    private Double shipping;
    private Double total;
    private String shippingAddressId;
    private String billingAddressId;
    private String paymentStatus; // PAYMENT_PENDING PAID
    private String fulfillmentStatus; // CONFIRMED SHIPPED DELIVERED CANCELLED REFUNDED

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
        if (cartId == null || cartId.isBlank()) return false;
        if (subtotal == null || subtotal < 0) return false;
        if (total == null || total < 0) return false;
        if (paymentStatus == null || paymentStatus.isBlank()) return false;
        if (fulfillmentStatus == null || fulfillmentStatus.isBlank()) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private String productId;
        private Integer qty;
        private Double price;
    }
}