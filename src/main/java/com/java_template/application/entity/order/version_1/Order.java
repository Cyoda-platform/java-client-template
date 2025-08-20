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
    private String orderId; // business order number visible to users (serialized UUID as String)
    private String customerId; // reference to customer (serialized UUID as String)
    private List<OrderItem> items; // line items
    private Double totalAmount; // calculated total
    private String currency; // currency code
    private Address shippingAddress; // address details
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
        if (shippingAddress == null) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private String sku;
        private Integer quantity;
        private Double price;

        public OrderItem() {}
    }

    @Data
    public static class Address {
        private String line1;
        private String line2;
        private String city;
        private String postalCode;
        private String country;

        public Address() {}
    }
}
