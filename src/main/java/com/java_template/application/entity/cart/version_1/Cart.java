package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cart Entity - Shopping session for anonymous users
 * 
 * Represents a shopping cart with line items, totals, and guest contact information.
 * State is managed via entity metadata (NEW → ACTIVE → CHECKING_OUT → CONVERTED).
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String cartId;                 // Unique cart identifier
    private List<CartLine> lines;          // Cart line items
    private Integer totalItems;            // Total quantity of items
    private Double grandTotal;             // Total cart value

    // Optional guest contact information
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
        return cartId != null && !cartId.trim().isEmpty() &&
               lines != null &&
               totalItems != null && totalItems >= 0 &&
               grandTotal != null && grandTotal >= 0;
    }

    /**
     * Cart line item with product information
     */
    @Data
    public static class CartLine {
        private String sku;                // Product SKU
        private String name;               // Product name (snapshot)
        private Double price;              // Product price (snapshot)
        private Integer qty;               // Quantity in cart
        private Double lineTotal;          // Line total (price * qty)
    }

    /**
     * Guest contact information for checkout
     */
    @Data
    public static class GuestContact {
        private String name;               // Guest name
        private String email;              // Guest email
        private String phone;              // Guest phone
        private GuestAddress address;      // Guest address
    }

    /**
     * Guest address information
     */
    @Data
    public static class GuestAddress {
        private String line1;              // Address line 1
        private String city;               // City
        private String postcode;           // Postal code
        private String country;            // Country
    }
}
