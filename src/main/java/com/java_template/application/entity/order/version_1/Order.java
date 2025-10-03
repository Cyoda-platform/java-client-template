package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity for multi-channel retailer order and inventory tracking service
 * 
 * Represents a customer order with complete lifecycle tracking from draft to delivery.
 * Supports multiple channels (web, store, marketplace) and comprehensive order management.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Primary business identifier
    private String orderId;
    
    // Core order identifiers and metadata
    private String externalRef;
    private String channel; // web, store, marketplace
    private LocalDateTime createdTimestamp;
    
    // Customer information
    private Customer customer;
    
    // Order line items
    private List<LineItem> lineItems;
    
    // Payment information
    private Payment payment;
    
    // Shipment information
    private Shipment shipment;
    
    // Calculated fields
    private Double totalAmount;
    private String currency;
    
    // Audit fields
    private LocalDateTime updatedTimestamp;
    private String lastUpdatedBy;

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
               channel != null && !channel.trim().isEmpty() &&
               customer != null && customer.isValid() &&
               lineItems != null && !lineItems.isEmpty() &&
               lineItems.stream().allMatch(LineItem::isValid);
    }

    /**
     * Customer information for the order
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
        private String preferredContactMethod;
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
        private boolean isDefault;
    }

    /**
     * Individual line item in the order
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
        private Double lineTotal;
        
        public boolean isValid() {
            return productId != null && !productId.trim().isEmpty() &&
                   quantity != null && quantity > 0 &&
                   unitPrice != null && unitPrice >= 0;
        }
    }

    /**
     * Payment information for the order
     */
    @Data
    public static class Payment {
        private String method; // credit_card, debit_card, paypal, bank_transfer, etc.
        private Double amount;
        private String currency;
        private String transactionRef;
        private String status; // pending, authorized, captured, failed, refunded
        private LocalDateTime authorizedAt;
        private LocalDateTime capturedAt;
        private LocalDateTime failedAt;
        private String failureReason;
    }

    /**
     * Shipment information for the order
     */
    @Data
    public static class Shipment {
        private String shipmentId;
        private String carrier;
        private String serviceLevel; // standard, express, overnight
        private String trackingNumber;
        private LocalDateTime estimatedDelivery;
        private LocalDateTime actualDelivery;
        private List<ShipmentEvent> events;
        private Address shippingAddress;
    }

    /**
     * Individual shipment tracking events
     */
    @Data
    public static class ShipmentEvent {
        private LocalDateTime timestamp;
        private String status; // picked_up, in_transit, out_for_delivery, delivered, exception
        private String location;
        private String description;
        private String eventCode;
    }
}
