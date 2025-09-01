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
    private String orderNumber;
    private String status;
    private String createdAt;
    private String updatedAt;
    private GuestContact guestContact;
    private List<Line> lines;
    private Totals totals;

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
        // Basic required field checks
        if (orderId == null || orderId.isBlank()) return false;
        if (orderNumber == null || orderNumber.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        if (guestContact == null || !guestContact.isValid()) return false;

        if (lines == null || lines.isEmpty()) return false;
        for (Line l : lines) {
            if (l == null || !l.isValid()) return false;
        }

        if (totals == null) return false;
        if (totals.getGrand() == null || totals.getGrand() < 0) return false;
        if (totals.getItems() == null || totals.getItems() < 0) return false;

        return true;
    }

    @Data
    public static class GuestContact {
        private Address address;
        private String email;
        private String name;
        private String phone;

        public boolean isValid() {
            if (email == null || email.isBlank()) return false;
            if (name == null || name.isBlank()) return false;
            if (address == null || !address.isValid()) return false;
            // phone may be empty
            return true;
        }
    }

    @Data
    public static class Address {
        private String city;
        private String country;
        private String line1;
        private String postcode;

        public boolean isValid() {
            if (city == null || city.isBlank()) return false;
            if (country == null || country.isBlank()) return false;
            if (line1 == null || line1.isBlank()) return false;
            if (postcode == null || postcode.isBlank()) return false;
            return true;
        }
    }

    @Data
    public static class Line {
        private Double lineTotal;
        private String name;
        private Integer qty;
        private String sku;
        private Double unitPrice;

        public boolean isValid() {
            if (sku == null || sku.isBlank()) return false;
            if (qty == null || qty <= 0) return false;
            if (unitPrice == null || unitPrice < 0) return false;
            if (lineTotal == null || lineTotal < 0) return false;
            return true;
        }
    }

    @Data
    public static class Totals {
        private Double grand;
        private Integer items;
    }
}