package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Payment Entity - Represents a dummy payment transaction for demo purposes.
 * 
 * Entity state is managed by the workflow system:
 * - INITIATED: Payment has been started
 * - PAID: Payment completed successfully
 * - FAILED: Payment failed
 * - CANCELED: Payment was canceled
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String paymentId;       // Required, unique
    private String cartId;          // Required: Associated cart identifier
    private Double amount;          // Required: Payment amount
    private String provider;        // Required: Payment provider (always "DUMMY")

    // Timestamps
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
               provider != null && "DUMMY".equals(provider);
    }
}
