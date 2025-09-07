package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cart Entity - Represents a shopping cart for anonymous checkout process
 * 
 * Workflow States (managed by entity.meta.state):
 * - NEW: Initial state
 * - ACTIVE: Has items, can be modified
 * - CHECKING_OUT: Guest contact added, ready for payment
 * - CONVERTED: Successfully converted to order
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core Fields
    private String cartId; // required, unique - Cart identifier
    private List<CartLine> lines; // required - Cart items
    private Integer totalItems; // required - Total quantity of items
    private Double grandTotal; // required - Total cart value
    private CartGuestContact guestContact; // optional - Guest contact information
    private LocalDateTime createdAt; // auto-generated
    private LocalDateTime updatedAt; // auto-generated

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return cartId != null && !cartId.trim().isEmpty() &&
               lines != null &&
               totalItems != null && totalItems >= 0 &&
               grandTotal != null && grandTotal >= 0;
    }

    /**
     * Nested class for cart line items
     */
    @Data
    public static class CartLine {
        private String sku; // Product SKU
        private String name; // Product name
        private Double price; // Product price
        private Integer qty; // Quantity
    }

    /**
     * Nested class for guest contact information
     */
    @Data
    public static class CartGuestContact {
        private String name; // optional
        private String email; // optional
        private String phone; // optional
        private CartAddress address; // optional
    }

    /**
     * Nested class for address information
     */
    @Data
    public static class CartAddress {
        private String line1; // optional
        private String city; // optional
        private String postcode; // optional
        private String country; // optional
    }
}
