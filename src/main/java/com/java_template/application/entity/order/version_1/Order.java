package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Confirmed purchase after successful payment
 * 
 * Represents a confirmed order with line items, totals, and customer information.
 * State is managed via entity metadata (WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED).
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String orderId;                // Unique order identifier
    private String orderNumber;            // Short ULID for customer reference
    private List<OrderLine> lines;         // Order line items
    private OrderTotals totals;            // Order totals
    private GuestContact guestContact;     // Customer contact information (required)

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
        return orderId != null && !orderId.trim().isEmpty() &&
               orderNumber != null && !orderNumber.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               totals != null &&
               guestContact != null && isValidGuestContact();
    }

    private boolean isValidGuestContact() {
        return guestContact.getName() != null && !guestContact.getName().trim().isEmpty() &&
               guestContact.getAddress() != null &&
               guestContact.getAddress().getLine1() != null && !guestContact.getAddress().getLine1().trim().isEmpty() &&
               guestContact.getAddress().getCity() != null && !guestContact.getAddress().getCity().trim().isEmpty() &&
               guestContact.getAddress().getPostcode() != null && !guestContact.getAddress().getPostcode().trim().isEmpty() &&
               guestContact.getAddress().getCountry() != null && !guestContact.getAddress().getCountry().trim().isEmpty();
    }

    /**
     * Order line item with product information
     */
    @Data
    public static class OrderLine {
        private String sku;                // Product SKU
        private String name;               // Product name (snapshot)
        private Double unitPrice;          // Unit price (snapshot)
        private Integer qty;               // Quantity ordered
        private Double lineTotal;          // Line total (unitPrice * qty)
    }

    /**
     * Order totals
     */
    @Data
    public static class OrderTotals {
        private Integer items;             // Total item count
        private Double grand;              // Grand total amount
    }

    /**
     * Customer contact information (required for orders)
     */
    @Data
    public static class GuestContact {
        private String name;               // Customer name (required)
        private String email;              // Customer email (optional)
        private String phone;              // Customer phone (optional)
        private GuestAddress address;      // Shipping address (required)
    }

    /**
     * Customer address information (required for orders)
     */
    @Data
    public static class GuestAddress {
        private String line1;              // Address line 1 (required)
        private String city;               // City (required)
        private String postcode;           // Postal code (required)
        private String country;            // Country (required)
    }
}
