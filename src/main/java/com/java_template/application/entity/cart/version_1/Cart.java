package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cart Entity - Represents a shopping cart for anonymous checkout process.
 * 
 * Entity state is managed by the workflow system:
 * - NEW: Initial state when cart is created
 * - ACTIVE: Cart has items and can be modified
 * - CHECKING_OUT: Cart is in checkout process
 * - CONVERTED: Cart has been converted to order
 */
@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = Cart.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String cartId;              // Required, unique
    private List<CartLine> lines;       // Required: Cart line items
    private Integer totalItems;         // Required: Total number of items in cart
    private Double grandTotal;          // Required: Total cart value

    // Optional fields
    private CartGuestContact guestContact;  // Optional: Guest contact information

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
        if (cartId == null || cartId.trim().isEmpty()) {
            return false;
        }
        if (lines == null) {
            return false;
        }
        if (totalItems == null || totalItems < 0) {
            return false;
        }
        if (grandTotal == null || grandTotal < 0) {
            return false;
        }

        // Validate that totalItems equals sum of all line quantities
        int calculatedTotalItems = lines.stream()
                .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
                .sum();
        if (!totalItems.equals(calculatedTotalItems)) {
            return false;
        }

        // Validate that grandTotal equals sum of all line totals
        double calculatedGrandTotal = lines.stream()
                .mapToDouble(line -> {
                    if (line.getPrice() != null && line.getQty() != null) {
                        return line.getPrice() * line.getQty();
                    }
                    return 0.0;
                })
                .sum();
        if (Math.abs(grandTotal - calculatedGrandTotal) > 0.01) { // Allow for small floating point differences
            return false;
        }

        // Validate lines
        for (CartLine line : lines) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                return false;
            }
            if (line.getQty() == null || line.getQty() <= 0) {
                return false;
            }
            if (line.getPrice() == null || line.getPrice() < 0) {
                return false;
            }
        }

        // Validate guest contact if provided
        if (guestContact != null && guestContact.getAddress() != null) {
            if (guestContact.getAddress().getLine1() == null || 
                guestContact.getAddress().getLine1().trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Cart line item with sku, name, price, qty
     */
    @Data
    public static class CartLine {
        private String sku;     // Product SKU
        private String name;    // Product name (from Product entity)
        private Double price;   // Product price (from Product entity)
        private Integer qty;    // Quantity in cart
    }

    /**
     * Guest contact information including name, email, phone, address
     */
    @Data
    public static class CartGuestContact {
        private String name;
        private String email;
        private String phone;
        private CartAddress address;
    }

    /**
     * Guest address information
     */
    @Data
    public static class CartAddress {
        private String line1;       // Required when address is provided
        private String city;
        private String postcode;
        private String country;
    }
}
