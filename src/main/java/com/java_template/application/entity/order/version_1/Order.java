package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Order entity representing customer orders with fulfillment tracking,
 * line items, totals, and guest contact information for the OMS system.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String orderId; // Business ID - unique identifier
    private String orderNumber; // Short ULID for customer reference
    private String status; // "WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
    private List<OrderLine> lines;
    private OrderTotals totals;
    private OrderGuestContact guestContact;
    
    // Optional fields
    private String cartId; // Reference to original cart
    private String paymentId; // Reference to payment
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
    public boolean isValid(org.cyoda.cloud.api.event.common.EntityMetadata metadata) {
        return orderId != null && !orderId.trim().isEmpty() &&
               orderNumber != null && !orderNumber.trim().isEmpty() &&
               status != null && !status.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               totals != null &&
               guestContact != null;
    }

    /**
     * Order line item representing a product in the order
     */
    @Data
    public static class OrderLine {
        private String sku;
        private String name;
        private Double unitPrice;
        private Integer qty;
        private Double lineTotal; // unitPrice * qty
    }

    /**
     * Order totals breakdown
     */
    @Data
    public static class OrderTotals {
        private Integer items; // Total quantity of items
        private Double grand; // Grand total amount
    }

    /**
     * Guest contact information copied from cart
     */
    @Data
    public static class OrderGuestContact {
        private String name;
        private String email;
        private String phone;
        private OrderAddress address;
    }

    /**
     * Shipping address information
     */
    @Data
    public static class OrderAddress {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}
