package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Customer orders for OMS system
 * 
 * This entity represents customer orders created from converted carts.
 * Entity state is managed by workflow: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core Fields
    private String orderId;                // Unique order identifier
    private String orderNumber;            // Short ULID for customer reference
    private List<OrderLine> lines;         // Order line items
    private OrderTotals totals;            // Order totals
    private GuestContact guestContact;     // Guest contact information (required)

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
               guestContact != null && guestContact.isValid();
    }

    /**
     * OrderLine Object - Individual line item in order
     */
    @Data
    public static class OrderLine {
        private String sku;                // Product SKU
        private String name;               // Product name (snapshot)
        private BigDecimal unitPrice;      // Unit price (snapshot)
        private Integer qty;               // Quantity ordered
        private BigDecimal lineTotal;      // Line total (unitPrice * qty)
    }

    /**
     * OrderTotals Object - Order totals
     */
    @Data
    public static class OrderTotals {
        private BigDecimal items;          // Items total
        private BigDecimal grand;          // Grand total
    }

    /**
     * GuestContact Object - Guest contact information (required for orders)
     */
    @Data
    public static class GuestContact {
        private String name;               // Guest name (required)
        private String email;              // Guest email (nullable)
        private String phone;              // Guest phone (nullable)
        private Address address;           // Guest address (required)

        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    /**
     * Address Object - Address information (required fields for orders)
     */
    @Data
    public static class Address {
        private String line1;              // Address line 1 (required)
        private String city;               // City (required)
        private String postcode;           // Postal code (required)
        private String country;            // Country (required)

        public boolean isValid() {
            return line1 != null && !line1.trim().isEmpty() &&
                   city != null && !city.trim().isEmpty() &&
                   postcode != null && !postcode.trim().isEmpty() &&
                   country != null && !country.trim().isEmpty();
        }
    }
}
