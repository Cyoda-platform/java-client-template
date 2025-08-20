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

    private String orderId; // business order number visible to users
    private String customerId; // serialized UUID reference
    private List<OrderItem> items; // line items
    private Double totalAmount; // calculated total
    private String currency; // currency code
    private String shippingAddress; // serialized JSON address
    private String status; // workflow-driven state
    private String createdAt; // ISO timestamp

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
        if (currency == null || currency.isBlank()) return false;
        if (shippingAddress == null || shippingAddress.isBlank()) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private String sku;
        private Integer quantity;
        private Double price;

        public boolean isValid() {
            if (sku == null || sku.isBlank()) return false;
            if (quantity == null || quantity <= 0) return false;
            if (price == null || price < 0) return false;
            return true;
        }
    }
}
