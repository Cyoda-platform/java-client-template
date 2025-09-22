package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Payment Entity - Dummy payment processing for the OMS system
 * 
 * This entity represents a payment transaction with auto-approval
 * after 3 seconds for demo purposes.
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String paymentId; // Business ID - unique identifier
    private String cartId; // Reference to cart
    private Double amount; // Payment amount
    private String status; // "INITIATED", "PAID", "FAILED", "CANCELED"
    private String provider; // "DUMMY"
    
    // Optional fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt; // When payment was marked as paid
    private String failureReason; // If payment failed

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return paymentId != null && !paymentId.trim().isEmpty() &&
               cartId != null && !cartId.trim().isEmpty() &&
               amount != null && amount > 0 &&
               status != null && isValidStatus(status) &&
               provider != null && !provider.trim().isEmpty();
    }

    /**
     * Validate payment status values
     */
    private boolean isValidStatus(String status) {
        return "INITIATED".equals(status) || 
               "PAID".equals(status) || 
               "FAILED".equals(status) || 
               "CANCELED".equals(status);
    }
}
