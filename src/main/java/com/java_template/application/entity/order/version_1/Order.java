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
    private String id;
    private String cartId; // serialized UUID reference
    private String createdAt; // ISO timestamp as String
    private List<OrderItem> items;
    private String orderNumber;
    private String status; // enum as String
    private Double totalAmount;
    private UserSnapshot userSnapshot;

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
        if (orderNumber == null || orderNumber.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (totalAmount == null || totalAmount < 0) return false;
        if (items == null || items.isEmpty()) return false;
        for (OrderItem item : items) {
            if (item == null || !item.isValid()) return false;
        }
        if (userSnapshot == null || !userSnapshot.isValid()) return false;
        return true;
    }

    @Data
    public static class OrderItem {
        private Double price;
        private String productId; // serialized UUID reference
        private Integer qtyFulfilled;
        private Integer qtyOrdered;

        public boolean isValid() {
            if (productId == null || productId.isBlank()) return false;
            if (price == null || price < 0) return false;
            if (qtyOrdered == null || qtyOrdered <= 0) return false;
            if (qtyFulfilled == null) return false;
            if (qtyFulfilled < 0) return false;
            if (qtyFulfilled > qtyOrdered) return false;
            return true;
        }
    }

    @Data
    public static class UserSnapshot {
        private Address address;
        private String email;
        private String name;

        public boolean isValid() {
            if (email == null || email.isBlank()) return false;
            if (name == null || name.isBlank()) return false;
            if (address == null || !address.isValid()) return false;
            return true;
        }
    }

    @Data
    public static class Address {
        private String city;
        private String country;
        private String line1;
        private String line2;
        private String postal;

        public boolean isValid() {
            if (line1 == null || line1.isBlank()) return false;
            if (city == null || city.isBlank()) return false;
            if (country == null || country.isBlank()) return false;
            if (postal == null || postal.isBlank()) return false;
            return true;
        }
    }
}