package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Payment Entity - Dummy payment processing for the OMS
 * 
 * This entity represents a payment transaction with dummy auto-approval after ~3 seconds.
 * Status transitions: INITIATED → PAID | FAILED | CANCELED
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String paymentId; // unique business identifier
    private String cartId; // reference to the cart being paid for
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
               status != null && !status.trim().isEmpty() &&
               provider != null && !provider.trim().isEmpty();
    }
}
