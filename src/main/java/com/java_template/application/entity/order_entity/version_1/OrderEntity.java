package com.java_template.application.entity.order_entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Order Entity - Represents purchase orders for pets, tracking sales transactions and performance metrics
 */
@Data
public class OrderEntity implements CyodaEntity {
    public static final String ENTITY_NAME = OrderEntity.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core order data from Pet Store API
    private Long orderId;
    private Long petId;
    private Integer quantity;
    private Double unitPrice;
    private Double totalAmount;
    private LocalDateTime shipDate;
    private LocalDateTime orderDate;
    private CustomerInfo customerInfo;
    private Boolean complete;

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
        if (orderId == null) {
            return false;
        }
        if (petId == null) {
            return false;
        }
        if (quantity == null || quantity <= 0) {
            return false;
        }
        if (unitPrice == null || unitPrice < 0) {
            return false;
        }
        if (totalAmount == null || totalAmount < 0) {
            return false;
        }
        if (orderDate == null) {
            return false;
        }
        // Validate calculated total amount
        if (Math.abs(totalAmount - (quantity * unitPrice)) > 0.01) {
            return false;
        }
        // Ship date cannot be before order date
        if (shipDate != null && orderDate != null && shipDate.isBefore(orderDate)) {
            return false;
        }
        return true;
    }

    /**
     * Nested class for customer information
     */
    @Data
    public static class CustomerInfo {
        private String name;
        private String email;
        private String phone;
    }
}
