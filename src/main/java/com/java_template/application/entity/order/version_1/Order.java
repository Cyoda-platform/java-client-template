package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String orderId;
    private String orderNumber;
    private String userId; // foreign key (serialized UUID)
    private String status; // enum represented as String
    private String createdAt; // ISO timestamp
    private String updatedAt; // ISO timestamp

    private Address shippingAddress;
    private Totals totals;
    private List<Line> lines;

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
        // Basic required string fields
        if (orderId == null || orderId.isBlank()) return false;
        if (orderNumber == null || orderNumber.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Lines must exist and each line must be valid
        if (lines == null || lines.isEmpty()) return false;
        for (Line l : lines) {
            if (l == null || !l.isValid()) return false;
        }

        // Totals must exist and have at least grand and items defined
        if (totals == null || !totals.isValid()) return false;

        // Shipping address should be present and valid
        if (shippingAddress == null || !shippingAddress.isValid()) return false;

        return true;
    }

    @Data
    public static class Line {
        private String sku;
        private String name;
        private Integer qty;
        private Double unitPrice;
        private Double lineTotal;

        public boolean isValid() {
            if (sku == null || sku.isBlank()) return false;
            if (name == null || name.isBlank()) return false;
            if (qty == null || qty <= 0) return false;
            if (unitPrice == null || unitPrice < 0.0) return false;
            if (lineTotal == null || lineTotal < 0.0) return false;
            return true;
        }
    }

    @Data
    public static class Address {
        private String line1;
        private String city;
        private String postcode;
        private String country;

        public boolean isValid() {
            if (line1 == null || line1.isBlank()) return false;
            if (city == null || city.isBlank()) return false;
            if (postcode == null || postcode.isBlank()) return false;
            if (country == null || country.isBlank()) return false;
            return true;
        }
    }

    @Data
    public static class Totals {
        private Double items;
        private Double shipping;
        private Double tax;
        private Double grand;

        public boolean isValid() {
            if (grand == null) return false;
            if (items == null) return false;
            // shipping and tax can be zero but must be non-null
            if (shipping == null) return false;
            if (tax == null) return false;
            return true;
        }
    }
}