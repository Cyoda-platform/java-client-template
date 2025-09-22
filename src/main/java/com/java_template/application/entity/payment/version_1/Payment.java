package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Payment Entity - Dummy payment processing for OMS
 * 
 * Represents a payment transaction with dummy provider that auto-approves after 3 seconds.
 * Supports workflow states: INITIATED → PAID | FAILED | CANCELED
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String paymentId;
    
    // Required fields
    private String cartId; // Reference to the cart being paid for
    private Double amount;
    private String status; // "INITIATED" | "PAID" | "FAILED" | "CANCELED"
    private String provider; // "DUMMY"
    
    // Optional fields
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
        return paymentId != null && !paymentId.trim().isEmpty() &&
               cartId != null && !cartId.trim().isEmpty() &&
               amount != null && amount > 0 &&
               status != null && isValidStatus(status) &&
               provider != null && !provider.trim().isEmpty();
    }

    private boolean isValidStatus(String status) {
        return "INITIATED".equals(status) || 
               "PAID".equals(status) || 
               "FAILED".equals(status) || 
               "CANCELED".equals(status);
    }
}
