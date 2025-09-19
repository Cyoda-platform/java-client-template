package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cart Entity - Shopping cart for OMS
 * 
 * Represents a shopping cart with items, totals, and guest contact information.
 * Supports workflow states: NEW → ACTIVE → CHECKING_OUT → CONVERTED
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String cartId; // required, unique business identifier

    // Required core fields
    private String status; // required: "NEW" | "ACTIVE" | "CHECKING_OUT" | "CONVERTED"
    private List<CartLine> lines; // required: cart items
    private Integer totalItems; // required: total quantity of all items
    private Double grandTotal; // required: total price of all items

    // Optional fields
    private GuestContact guestContact; // guest information for checkout
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
               status != null && isValidStatus(status) &&
               lines != null &&
               totalItems != null && totalItems >= 0 &&
               grandTotal != null && grandTotal >= 0.0;
    }

    private boolean isValidStatus(String status) {
        return "NEW".equals(status) || 
               "ACTIVE".equals(status) || 
               "CHECKING_OUT".equals(status) || 
               "CONVERTED".equals(status);
    }

    /**
     * Cart line item representing a product in the cart
     */
    @Data
    public static class CartLine {
        private String sku; // product SKU
        private String name; // product name (snapshot)
        private Double price; // unit price (snapshot)
        private Integer qty; // quantity in cart
        private Double lineTotal; // calculated: price * qty
    }

    /**
     * Guest contact information for anonymous checkout
     */
    @Data
    public static class GuestContact {
        private String name;
        private String email;
        private String phone;
        private GuestAddress address;
    }

    /**
     * Guest shipping address
     */
    @Data
    public static class GuestAddress {
        private String line1; // required for checkout
        private String line2;
        private String city; // required for checkout
        private String state;
        private String postcode; // required for checkout
        private String country; // required for checkout
    }
}
