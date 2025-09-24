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
 * Represents a shopping cart with lines, totals, guest contact information,
 * and status tracking through the cart workflow.
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String cartId; // required, unique business identifier

    // Required core fields
    private String status; // "NEW" | "ACTIVE" | "CHECKING_OUT" | "CONVERTED"
    private List<CartLine> lines; // cart line items
    private Integer totalItems; // total quantity of items
    private Double grandTotal; // total amount

    // Optional fields
    private CartGuestContact guestContact; // guest contact information
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
               totalItems != null && totalItems >= 0 &&
               grandTotal != null && grandTotal >= 0.0;
    }

    /**
     * Cart line item with product details and quantities
     */
    @Data
    public static class CartLine {
        private String sku; // product SKU
        private String name; // product name
        private Double price; // unit price
        private Integer qty; // quantity
        private Double lineTotal; // calculated line total (price * qty)
    }

    /**
     * Guest contact information for anonymous checkout
     */
    @Data
    public static class CartGuestContact {
        private String name;
        private String email;
        private String phone;
        private CartGuestAddress address;
    }

    /**
     * Guest address information
     */
    @Data
    public static class CartGuestAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }
}
