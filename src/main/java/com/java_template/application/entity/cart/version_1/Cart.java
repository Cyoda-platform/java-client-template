package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Cart entity representing shopping cart with lines, totals, and guest contact information
 * for anonymous checkout functionality.
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String cartId; // Business ID
    private String status; // "NEW" | "ACTIVE" | "CHECKING_OUT" | "CONVERTED"
    private List<CartLine> lines;
    private Integer totalItems;
    private Double grandTotal;

    // Optional fields
    private CartGuestContact guestContact;
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
        return cartId != null && !cartId.trim().isEmpty() &&
               status != null && !status.trim().isEmpty() &&
               lines != null &&
               totalItems != null && totalItems >= 0 &&
               grandTotal != null && grandTotal >= 0;
    }

    /**
     * Cart line item representing a product in the cart
     */
    @Data
    public static class CartLine {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
    }

    /**
     * Guest contact information for anonymous checkout
     */
    @Data
    public static class CartGuestContact {
        private String name;
        private String email;
        private String phone;
        private CartAddress address;
    }

    /**
     * Address information for guest contact
     */
    @Data
    public static class CartAddress {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}
