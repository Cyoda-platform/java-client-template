package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Payment Entity - Represents a dummy payment for demonstration purposes
 * 
 * Workflow States (managed by entity.meta.state):
 * - INITIATED: Payment started
 * - PAID: Payment successful (auto after 3s)
 * - FAILED: Payment failed
 * - CANCELED: Payment canceled
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core Fields
    private String paymentId; // required, unique - Payment identifier
    private String cartId; // required - Reference to cart
    private Double amount; // required - Payment amount
    private String provider; // required - Always "DUMMY" for demo
    private LocalDateTime createdAt; // auto-generated
    private LocalDateTime updatedAt; // auto-generated

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
        return paymentId != null && !paymentId.trim().isEmpty() &&
               cartId != null && !cartId.trim().isEmpty() &&
               amount != null && amount > 0 &&
               provider != null && !provider.trim().isEmpty();
    }
}
