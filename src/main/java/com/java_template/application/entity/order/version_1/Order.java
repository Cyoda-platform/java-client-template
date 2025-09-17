package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Order Entity - Represents a purchase order for pets in the store
 * 
 * This entity manages purchase orders with customer information and shipping details.
 * The status field from the Petstore API is replaced by the entity.meta.state system.
 * 
 * States: placed, approved, delivered, cancelled, returned
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String orderId;
    
    // Required core business fields
    private String petId;
    private Integer quantity;
    private CustomerInfo customerInfo;

    // Optional fields for additional business data
    private LocalDateTime shipDate;
    private Boolean complete;
    private Double totalAmount;
    private String paymentMethod;
    private Address shippingAddress;
    private String orderNotes;
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
        return orderId != null && !orderId.trim().isEmpty() &&
               petId != null && !petId.trim().isEmpty() &&
               quantity != null && quantity > 0 &&
               customerInfo != null;
    }

    /**
     * Nested class for customer information
     */
    @Data
    public static class CustomerInfo {
        private String customerId;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
    }

    /**
     * Nested class for address information
     */
    @Data
    public static class Address {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }
}
