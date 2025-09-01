package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on requirements prototype
    private String cartId;
    private String createdAt; // ISO timestamp as String
    private String updatedAt; // ISO timestamp as String
    private Double grandTotal;
    private Integer totalItems;
    private String status; // use String for enum-like values

    // Guest contact information for guest checkout
    private GuestContact guestContact;

    // Cart lines
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
        // cartId is required
        if (cartId == null || cartId.isBlank()) return false;

        // grandTotal should be non-null and non-negative
        if (grandTotal == null || grandTotal < 0) return false;

        // totalItems should be non-null and non-negative
        if (totalItems == null || totalItems < 0) return false;

        // status should be present
        if (status == null || status.isBlank()) return false;

        // lines must be present (cart should contain lines) and each line must be valid
        if (lines == null || lines.isEmpty()) return false;
        for (Line line : lines) {
            if (line == null || !line.isValid()) return false;
        }

        // guestContact if present should be valid
        if (guestContact != null && !guestContact.isValid()) return false;

        return true;
    }

    @Data
    public static class GuestContact {
        private Address address;
        private String email;
        private String name;
        private String phone;

        public boolean isValid() {
            // if no guest contact details provided, consider invalid (guest carts should include some contact)
            if ((email == null || email.isBlank()) && (name == null || name.isBlank()) && (phone == null || phone.isBlank())) {
                return false;
            }
            // basic check for email presence if provided
            if (email != null && email.isBlank()) return false;
            // address if present should be valid
            if (address != null && !address.isValid()) return false;
            return true;
        }

        @Data
        public static class Address {
            private String line1;
            private String city;
            private String postcode;
            private String country;

            public boolean isValid() {
                // require at least line1 and country when address provided
                if (line1 == null || line1.isBlank()) return false;
                if (country == null || country.isBlank()) return false;
                return true;
            }
        }
    }

    @Data
    public static class Line {
        private String name;
        private String sku;
        private Double price;
        private Integer qty;

        public boolean isValid() {
            // sku and qty and price are required
            if (sku == null || sku.isBlank()) return false;
            if (qty == null || qty <= 0) return false;
            if (price == null || price < 0) return false;
            // name can be optional but if present must not be blank
            if (name != null && name.isBlank()) return false;
            return true;
        }
    }
}