package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String cartId;
    private String createdAt;
    private String updatedAt;
    private Double grandTotal;
    private Integer totalItems;
    private String status;
    private GuestContact guestContact;
    private List<Line> lines;

    public Cart() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic required string checks
        if (cartId == null || cartId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (lines == null || lines.isEmpty()) return false;

        // Validate lines
        int computedTotalItems = 0;
        double computedGrand = 0.0;
        for (Line l : lines) {
            if (l == null) return false;
            if (l.getSku() == null || l.getSku().isBlank()) return false;
            if (l.getName() == null || l.getName().isBlank()) return false;
            if (l.getQty() == null || l.getQty() <= 0) return false;
            if (l.getPrice() == null || l.getPrice() < 0.0) return false;
            computedTotalItems += l.getQty();
            computedGrand += l.getPrice() * l.getQty();
        }

        // If totalItems provided, it should match sum of line qty
        if (totalItems != null && totalItems.intValue() != computedTotalItems) return false;

        // If grandTotal provided, allow small rounding tolerance when comparing
        if (grandTotal != null) {
            double diff = Math.abs(grandTotal - computedGrand);
            if (diff > 0.01) return false;
        }

        // Validate guest contact if present
        if (guestContact != null) {
            if (guestContact.getEmail() == null || guestContact.getEmail().isBlank()) return false;
            if (guestContact.getName() == null || guestContact.getName().isBlank()) return false;
            Address addr = guestContact.getAddress();
            if (addr != null) {
                if (addr.getLine1() == null || addr.getLine1().isBlank()) return false;
                if (addr.getCountry() == null || addr.getCountry().isBlank()) return false;
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
        private String city;
        private String country;
        private String line1;
        private String postcode;
    }

    @Data
    public static class Line {
        private String name;
        private Double price;
        private Integer qty;
        private String sku;
    }
}