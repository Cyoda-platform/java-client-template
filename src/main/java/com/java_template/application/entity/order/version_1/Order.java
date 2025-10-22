package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Order entity representing a customer order with line items,
 * totals, and guest contact information. Status is managed by workflow state.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String orderId;

    // Short ULID for order number
    private String orderNumber;

    // Order status: WAITING_TO_FULFILL, PICKING, WAITING_TO_SEND, SENT, DELIVERED
    // Note: This is managed by workflow state, not a business field
    private String status;

    // Order lines
    private List<OrderLine> lines;

    // Totals
    private OrderTotals totals;

    // Guest contact information
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
    public boolean isValid(EntityMetadata metadata) {
        return orderId != null && !orderId.trim().isEmpty() &&
               orderNumber != null && !orderNumber.trim().isEmpty();
    }

    /**
     * Order line item
     */
    @Data
    public static class OrderLine {
        private String sku;
        private String name;
        private Double unitPrice;
        private Integer qty;
        private Double lineTotal;
    }

    /**
     * Order totals
     */
    @Data
    public static class OrderTotals {
        private Integer items;
        private Double grand;
    }

    /**
     * Guest contact information
     */
    @Data
    public static class GuestContact {
        private String name;
        private String email;
        private String phone;
        private Address address;
    }

    /**
     * Address information
     */
    @Data
    public static class Address {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}

