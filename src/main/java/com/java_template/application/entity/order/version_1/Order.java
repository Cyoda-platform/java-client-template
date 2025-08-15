package com.java_template.application.entity.order.version_1; // replace {entityName} with actual entity name in lowercase

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

    private String orderId;
    private String customerId; // references User.id
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String currency;
    private String status; // CREATED, PAYMENT_PENDING, PAYMENT_FAILED, COMPLETED, CANCELLED
    private String placedAt; // ISO8601

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
        if (customerId == null || customerId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;

        public OrderItem() {}
    }
}
