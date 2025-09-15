package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Represents a customer order for food delivery
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String orderId;
    
    // Required core business fields
    private String restaurantId;
    private String customerId;
    private List<OrderItem> items;
    private Double totalAmount;
    private CustomerInfo customer;
    private DeliveryAddress deliveryAddress;
    
    // Optional fields for additional business data
    private Double subtotal;
    private Double deliveryFee;
    private Double serviceFee;
    private Double tax;
    private Double tip;
    private String currency;
    private String specialInstructions;
    private LocalDateTime requestedDeliveryTime;
    private LocalDateTime estimatedDeliveryTime;
    private LocalDateTime actualDeliveryTime;
    private String paymentMethod;
    private String paymentStatus;
    private String assignedDeliveryService;
    private String deliveryPersonId;
    private String trackingUrl;
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
        // Validate required fields
        return orderId != null && restaurantId != null && customerId != null && 
               items != null && totalAmount != null && customer != null && 
               deliveryAddress != null;
    }

    /**
     * Nested class for order items
     */
    @Data
    public static class OrderItem {
        private String menuItemId;
        private String name;
        private Integer quantity;
        private Double unitPrice;
        private List<OrderItemCustomization> customizations;
        private Double itemTotal;
    }

    /**
     * Nested class for order item customizations
     */
    @Data
    public static class OrderItemCustomization {
        private String customizationId;
        private String selectedOption;
        private Double additionalPrice;
    }

    /**
     * Nested class for customer information
     */
    @Data
    public static class CustomerInfo {
        private String name;
        private String phone;
        private String email;
    }

    /**
     * Nested class for delivery address
     */
    @Data
    public static class DeliveryAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
        private Double latitude;
        private Double longitude;
        private String deliveryInstructions;
    }
}
