package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String cartId;
    private String createdAt;
    private Double grandTotal;
    private GuestContact guestContact;
    private List<Line> lines = new ArrayList<>();
    private String status;
    private Integer totalItems;
    private String updatedAt;

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
        // cartId must be present
        if (cartId == null || cartId.isBlank()) {
            return false;
        }
        // createdAt must be present
        if (createdAt == null || createdAt.isBlank()) {
            return false;
        }
        // grandTotal must be present and non-negative
        if (grandTotal == null || grandTotal < 0) {
            return false;
        }
        // status must be present
        if (status == null || status.isBlank()) {
            return false;
        }
        // totalItems must be present and non-negative
        if (totalItems == null || totalItems < 0) {
            return false;
        }
        // lines must be present and each line valid
        if (lines == null) {
            return false;
        }
        for (Line l : lines) {
            if (l == null || !l.isValid()) {
                return false;
            }
        }
        // guestContact if present must be valid
        if (guestContact != null && !guestContact.isValid()) {
            return false;
        }
        return true;
    }

    @Data
    public static class GuestContact {
        private Address address;
        private String email;
        private String name;
        private String phone;

        public boolean isValid() {
            // name and email required when guest contact is provided
            if (name == null || name.isBlank()) {
                return false;
            }
            if (email == null || email.isBlank()) {
                return false;
            }
            // address must be present and valid
            if (address == null || !address.isValid()) {
                return false;
            }
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
            if (line1 == null || line1.isBlank()) {
                return false;
            }
            if (country == null || country.isBlank()) {
                return false;
            }
            if (postcode == null || postcode.isBlank()) {
                return false;
            }
            return true;
        }
    }

    @Data
    public static class Line {
        private String name;
        private Double price;
        private Integer qty;
        private String sku;

        public boolean isValid() {
            if (name == null || name.isBlank()) {
                return false;
            }
            if (sku == null || sku.isBlank()) {
                return false;
            }
            if (price == null || price < 0) {
                return false;
            }
            if (qty == null || qty <= 0) {
                return false;
            }
            return true;
        }
    }
}