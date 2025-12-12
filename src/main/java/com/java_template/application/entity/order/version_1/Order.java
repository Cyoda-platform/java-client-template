package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Order Entity for Order Management System
 * 
 * This entity represents an order in the system with lifecycle management
 * through states: initial, pending, confirmed, shipped, delivered, cancelled.
 * 
 * Business fields include order identification, customer reference, items,
 * pricing, and metadata for extensibility.
 */
@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = Order.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String orderId;
    
    // Required core business fields
    private String customerId;
    private String customerName;
    private String customerEmail;
    
    // Order details
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String currency;
    
    // Optional fields
    private String shippingAddress;
    private String billingAddress;
    private String notes;
    private Map<String, Object> metadata;
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
               customerName != null && !customerName.trim().isEmpty() &&
               customerEmail != null && !customerEmail.trim().isEmpty() && isValidEmail(customerEmail) &&
               items != null && !items.isEmpty() &&
               totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Convenience method for validation without metadata
     */
    public boolean isValid() {
        return isValid(null);
    }
    
    /**
     * Basic email validation
     */
    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    /**
     * Nested class for order items
     */
    @Data
    public static class OrderItem {
        private String itemId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private Map<String, Object> metadata;
    }
}

