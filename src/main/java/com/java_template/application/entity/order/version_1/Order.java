package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Represents a customer order created from a paid cart
 * 
 * Workflow States (managed by entity.meta.state):
 * - WAITING_TO_FULFILL: Order created, waiting to start fulfillment
 * - PICKING: Items being picked from warehouse
 * - WAITING_TO_SEND: Picked, waiting for shipment
 * - SENT: Shipped to customer
 * - DELIVERED: Delivered to customer
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core Fields
    private String orderId; // required, unique - Order identifier
    private String orderNumber; // required, unique - Short ULID for display
    private List<OrderLine> lines; // required - Order items
    private OrderTotals totals; // required - Order totals
    private OrderGuestContact guestContact; // required - Customer contact information
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
        return orderId != null && !orderId.trim().isEmpty() &&
               orderNumber != null && !orderNumber.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               totals != null &&
               guestContact != null && guestContact.isValid();
    }

    /**
     * Nested class for order line items
     */
    @Data
    public static class OrderLine {
        private String sku; // Product SKU
        private String name; // Product name
        private Double unitPrice; // Unit price
        private Integer qty; // Quantity
        private Double lineTotal; // Line total (unitPrice * qty)
    }

    /**
     * Nested class for order totals
     */
    @Data
    public static class OrderTotals {
        private Integer items; // Total items count
        private Double grand; // Grand total amount
    }

    /**
     * Nested class for guest contact information
     */
    @Data
    public static class OrderGuestContact {
        private String name; // required
        private String email; // optional
        private String phone; // optional
        private OrderAddress address; // required

        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   address != null && address.isValid();
        }
    }

    /**
     * Nested class for address information
     */
    @Data
    public static class OrderAddress {
        private String line1; // required
        private String city; // required
        private String postcode; // required
        private String country; // required

        public boolean isValid() {
            return line1 != null && !line1.trim().isEmpty() &&
                   city != null && !city.trim().isEmpty() &&
                   postcode != null && !postcode.trim().isEmpty() &&
                   country != null && !country.trim().isEmpty();
        }
    }
}
