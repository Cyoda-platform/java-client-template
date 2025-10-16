package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Order entity represents purchase orders placed by customers for pets
 * with customer information, pet details, pricing, and fulfillment status managed through workflow states.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String orderId;
    
    // Required core business fields
    private String customerId; // Reference to customer who placed the order
    private String petId; // Reference to pet being ordered
    private Integer quantity; // Quantity of pets ordered (typically 1)

    // Optional fields for additional business data
    private LocalDateTime orderDate;
    private LocalDateTime shipDate;
    private LocalDateTime deliveryDate;
    private OrderPricing pricing;
    private ShippingInfo shipping;
    private PaymentInfo payment;
    private String notes;
    private Boolean complete;
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
        // Validate required fields
        return orderId != null && !orderId.trim().isEmpty() &&
               customerId != null && !customerId.trim().isEmpty() &&
               petId != null && !petId.trim().isEmpty() &&
               quantity != null && quantity > 0 &&
               (pricing == null || pricing.getTotalAmount() == null || pricing.getTotalAmount() >= 0);
    }

    /**
     * Nested class for pricing breakdown
     */
    @Data
    public static class OrderPricing {
        private Double petPrice;
        private Double tax;
        private Double shippingCost;
        private Double discount;
        private Double totalAmount;
    }

    /**
     * Nested class for shipping information
     */
    @Data
    public static class ShippingInfo {
        private String method; // pickup, delivery, etc.
        private ShippingAddress address;
        private String trackingNumber;
    }

    /**
     * Nested class for shipping address
     */
    @Data
    public static class ShippingAddress {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }

    /**
     * Nested class for payment information
     */
    @Data
    public static class PaymentInfo {
        private String method; // credit_card, cash, etc.
        private String transactionId;
        private LocalDateTime paymentDate;
    }
}
