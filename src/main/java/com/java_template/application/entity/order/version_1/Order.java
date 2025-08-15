package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;

    // Business fields
    private String id; // business order id
    private String customerId; // reference to User.id (serialized UUID)
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal shipping;
    private BigDecimal total;
    private String status; // PENDING, PAID, PROCESSING, SHIPPED, CANCELLED, FAILED
    private String paymentReference; // provider transaction id or reference
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp

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
        if (id == null || id.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        for (OrderItem it : items) {
            if (it == null) return false;
            if (it.getProductId() == null || it.getProductId().isBlank()) return false;
            if (it.getQuantity() == null || it.getQuantity() <= 0) return false;
            if (it.getUnitPrice() == null || it.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) return false;
        }
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) < 0) return false;
        if (tax == null || tax.compareTo(BigDecimal.ZERO) < 0) return false;
        if (shipping == null || shipping.compareTo(BigDecimal.ZERO) < 0) return false;
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private String productId; // reference to Product.id (serialized UUID)
        private Integer quantity;
        private BigDecimal unitPrice;

        public OrderItem() {}
    }
}
