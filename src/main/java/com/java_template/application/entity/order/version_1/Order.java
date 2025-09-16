package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Entity - Represents customer orders
 * 
 * This entity manages order information including items, shipping,
 * and payment details. State is managed automatically by the
 * workflow system via entity.meta.state.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String orderId;
    
    // Required core business fields
    private String userId;
    private List<OrderItem> items;
    private Double totalAmount;
    private LocalDateTime orderDate;
    private Address shippingAddress;

    // Optional fields for additional business data
    private LocalDateTime shipDate;
    private String paymentMethod;
    private String notes;
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
        // Validate required fields according to functional requirements
        return orderId != null && !orderId.trim().isEmpty() &&
               userId != null && !userId.trim().isEmpty() &&
               items != null && !items.isEmpty() &&
               totalAmount != null && totalAmount > 0 &&
               orderDate != null &&
               shippingAddress != null;
    }

    /**
     * Nested class for order items
     * Contains information about each pet in the order
     */
    @Data
    public static class OrderItem {
        private String petId;
        private String petName;
        private Integer quantity;
        private Double unitPrice;
        private Double totalPrice;
    }

    /**
     * Nested class for address information
     * Used for shipping addresses
     */
    @Data
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }
}
