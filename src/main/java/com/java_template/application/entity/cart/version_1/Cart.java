package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cart Entity - Shopping cart for OMS system
 * 
 * This entity represents shopping carts with line items, totals, and guest contact information.
 * Entity state is managed by workflow: NEW → ACTIVE → CHECKING_OUT → CONVERTED
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core Fields
    private String cartId;                 // Unique cart identifier
    private List<CartLine> lines;          // Cart line items
    private Integer totalItems;            // Total number of items in cart
    private BigDecimal grandTotal;         // Total cart value
    private GuestContact guestContact;     // Guest contact information (nullable)

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
     * CartLine Object - Individual line item in cart
     */
    @Data
    public static class CartLine {
        private String sku;                // Product SKU
        private String name;               // Product name (snapshot)
        private BigDecimal price;          // Product price (snapshot)
        private Integer qty;               // Quantity
    }

    /**
     * GuestContact Object - Guest contact information for anonymous checkout
     */
    @Data
    public static class GuestContact {
        private String name;               // Guest name (nullable)
        private String email;              // Guest email (nullable)
        private String phone;              // Guest phone (nullable)
        private Address address;           // Guest address (nullable)
    }

    /**
     * Address Object - Address information
     */
    @Data
    public static class Address {
        private String line1;              // Address line 1 (nullable)
        private String city;               // City (nullable)
        private String postcode;           // Postal code (nullable)
        private String country;            // Country (nullable)
    }
}
