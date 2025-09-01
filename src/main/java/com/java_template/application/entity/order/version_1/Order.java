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

        // Lines validation
        if (lines == null || lines.isEmpty()) return false;
        for (Line l : lines) {
            if (l == null) return false;
            if (l.getSku() == null || l.getSku().isBlank()) return false;
            if (l.getQty() == null || l.getQty() <= 0) return false;
            if (l.getUnitPrice() == null || l.getUnitPrice() < 0) return false;
            if (l.getLineTotal() == null || l.getLineTotal() < 0) return false;
        }

        // Totals validation
        if (totals == null) return false;
        if (totals.getGrand() == null || totals.getGrand() < 0) return false;
        if (totals.getItems() == null || totals.getItems() < 0) return false;

        // Guest contact is optional, but if present validate basics
        if (guestContact != null) {
            if (guestContact.getEmail() != null && guestContact.getEmail().isBlank()) return false;
            if (guestContact.getName() != null && guestContact.getName().isBlank()) return false;
            if (guestContact.getPhone() != null && guestContact.getPhone().isBlank()) return false;
            Address a = guestContact.getAddress();
            if (a != null) {
                if (a.getLine1() != null && a.getLine1().isBlank()) return false;
                if (a.getCity() != null && a.getCity().isBlank()) return false;
                if (a.getCountry() != null && a.getCountry().isBlank()) return false;
                if (a.getPostcode() != null && a.getPostcode().isBlank()) return false;
            }
        }

        return true;
    }

    @Data
    public static class GuestContact {
        private Address address;
        private String email;
        private String name;
        private String phone;
    }

    @Data
    public static class Address {
        private String line1;
        private String city;
        private String country;
        private String postcode;
    }

    @Data
    public static class Line {
        private String sku;
        private String name;
        private Integer qty;
        private Double unitPrice;
        private Double lineTotal;
    }

    @Data
    public static class Totals {
        private Double grand;
        private Integer items;
    }
}