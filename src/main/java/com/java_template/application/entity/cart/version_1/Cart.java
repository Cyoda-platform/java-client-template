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
 * This entity represents a shopping cart with line items, totals,
 * and guest contact information for anonymous checkout.
 * 
 * Cart States: NEW → ACTIVE → CHECKING_OUT → CONVERTED
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String cartId;                 // required, unique business identifier
    private String status;                 // required: "NEW" | "ACTIVE" | "CHECKING_OUT" | "CONVERTED"
    private List<CartLine> lines;          // required: cart line items
    private Integer totalItems;            // required: total quantity of items
    private Double grandTotal;             // required: total amount

    // Optional fields
    private CartGuestContact guestContact; // optional: guest contact information
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
        private String sku;        // product SKU
        private String name;       // product name (snapshot)
        private Double price;      // product price (snapshot)
        private Integer qty;       // quantity in cart
        private Double lineTotal;  // calculated: price * qty
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
