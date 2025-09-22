package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Order management for the OMS system
 * 
 * This entity represents an order with ULID order numbers,
 * line items, totals, and guest contact information.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String orderId; // Business ID - unique identifier
    private String orderNumber; // Short ULID for customer reference
    private String status; // "WAITING_TO_FULFILL", "PICKING", "WAITING_TO_SEND", "SENT", "DELIVERED"
    private List<OrderLine> lines;
    private OrderTotals totals;
    private OrderGuestContact guestContact;
    
    // Optional fields
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
               status != null && isValidStatus(status) &&
               lines != null && !lines.isEmpty() &&
               totals != null &&
               guestContact != null && guestContact.isValid();
    }

    /**
     * Validate order status values
     */
    private boolean isValidStatus(String status) {
        return "WAITING_TO_FULFILL".equals(status) || 
               "PICKING".equals(status) || 
               "WAITING_TO_SEND".equals(status) || 
               "SENT".equals(status) || 
               "DELIVERED".equals(status);
    }

    /**
     * Order line item representing a product in the order
     */
    @Data
    public static class OrderLine {
        private String sku; // Product SKU
        private String name; // Product name (snapshot)
        private Double unitPrice; // Product price at time of order
        private Integer qty; // Quantity ordered
        private Double lineTotal; // unitPrice * qty
    }

    /**
     * Order totals breakdown
     */
    @Data
    public static class OrderTotals {
        private Double items; // Total of all line items
        private Double grand; // Grand total (same as items for this demo)
    }

    /**
     * Guest contact information (required for orders)
     */
    @Data
    public static class OrderGuestContact {
        private String name; // Required
        private String email;
        private String phone;
        private OrderAddress address; // Required

        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    /**
     * Address information for shipping (required)
     */
    @Data
    public static class OrderAddress {
        private String line1; // Required
        private String city; // Required
        private String postcode; // Required
        private String country; // Required

        public boolean isValid() {
            return line1 != null && !line1.trim().isEmpty() &&
                   city != null && !city.trim().isEmpty() &&
                   postcode != null && !postcode.trim().isEmpty() &&
                   country != null && !country.trim().isEmpty();
        }
    }
}
