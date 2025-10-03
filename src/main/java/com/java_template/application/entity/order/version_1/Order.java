package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Order entity represents a customer order in the multi-channel retail system
 * with support for order lifecycle management, payments, shipments, and fulfillment tracking.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String orderId;
    
    // Core order fields
    private String externalRef;
    private String channel; // web, store, marketplace
    private LocalDateTime createdTimestamp;
    
    // Customer information
    private Customer customer;
    
    // Order items
    private List<LineItem> lineItems;
    
    // Payment information
    private Payment payment;
    
    // Shipment information
    private Shipment shipment;

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
               externalRef != null && !externalRef.trim().isEmpty() &&
               channel != null && !channel.trim().isEmpty() &&
               customer != null && customer.isValid() &&
               lineItems != null && !lineItems.isEmpty() &&
               lineItems.stream().allMatch(LineItem::isValid);
    }

    /**
     * Customer information nested class
     */
    @Data
    public static class Customer {
        private String customerId;
        private String name;
        private ContactDetails contactDetails;
        private List<Address> addresses;

        public boolean isValid() {
            return customerId != null && !customerId.trim().isEmpty() &&
                   name != null && !name.trim().isEmpty();
        }
    }

    /**
     * Contact details for customer
     */
    @Data
    public static class ContactDetails {
        private String email;
        private String phone;
        private String alternatePhone;
    }

    /**
     * Address information
     */
    @Data
    public static class Address {
        private String type; // billing, shipping, etc.
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }

    /**
     * Line item representing products in the order
     */
    @Data
    public static class LineItem {
        private String productId;
        private String description;
        private Integer quantity;
        private Double unitPrice;
        private Double discount;
        private Double tax;
        private String fulfilmentStatus; // pending, reserved, packed, shipped, delivered

        public boolean isValid() {
            return productId != null && !productId.trim().isEmpty() &&
                   quantity != null && quantity > 0 &&
                   unitPrice != null && unitPrice >= 0;
        }

        public Double getLineTotal() {
            if (unitPrice == null || quantity == null) return 0.0;
            Double subtotal = unitPrice * quantity;
            if (discount != null) subtotal -= discount;
            if (tax != null) subtotal += tax;
            return subtotal;
        }
    }

    /**
     * Payment information
     */
    @Data
    public static class Payment {
        private String method; // credit_card, debit_card, paypal, cash, etc.
        private Double amount;
        private String currency;
        private String transactionRef;
        private String status; // pending, authorized, captured, failed, refunded
        private PaymentTimestamps timestamps;
    }

    /**
     * Payment timestamps
     */
    @Data
    public static class PaymentTimestamps {
        private LocalDateTime authorized;
        private LocalDateTime captured;
        private LocalDateTime failed;
        private LocalDateTime refunded;
    }

    /**
     * Shipment information
     */
    @Data
    public static class Shipment {
        private String shipmentId;
        private String carrier;
        private String serviceLevel; // standard, express, overnight
        private String trackingNumber;
        private LocalDateTime estimatedDelivery;
        private List<ShipmentEvent> events;
    }

    /**
     * Shipment tracking events
     */
    @Data
    public static class ShipmentEvent {
        private String eventType; // picked_up, in_transit, out_for_delivery, delivered
        private String description;
        private LocalDateTime timestamp;
        private String location;
    }

    /**
     * Calculate total order amount
     */
    public Double getOrderTotal() {
        if (lineItems == null || lineItems.isEmpty()) return 0.0;
        return lineItems.stream()
                .mapToDouble(item -> item.getLineTotal())
                .sum();
    }
}
