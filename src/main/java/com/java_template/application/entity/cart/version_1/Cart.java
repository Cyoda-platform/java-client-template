package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Cart entity representing a shopping cart with line items,
 * totals, and guest contact information for anonymous checkout.
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String cartId;

    // Cart status: NEW, ACTIVE, CHECKING_OUT, CONVERTED
    // Note: This is managed by workflow state, not a business field
    // We use entity metadata state instead of this field
    private String status;

    // Cart lines
    private List<CartLine> lines;

    // Totals
    private Integer totalItems;
    private Double grandTotal;

    // Guest contact information
    private GuestContact guestContact;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return cartId != null && !cartId.trim().isEmpty();
    }

    /**
     * Cart line item
     */
    @Data
    public static class CartLine {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
    }

    /**
     * Guest contact information
     */
    @Data
    public static class GuestContact {
        private String name;
        private String email;
        private String phone;
        private Address address;
    }

    /**
     * Address information
     */
    @Data
    public static class Address {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}

