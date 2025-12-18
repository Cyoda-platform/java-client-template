package com.example.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Payment Entity - Dummy payment for OMS
 * States: INITIATED → PAID | FAILED | CANCELED
 * Auto-approves to PAID after ~3 seconds
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = "Payment";
    public static final Integer ENTITY_VERSION = 1;

    // Business ID
    private String paymentId;

    // Reference to cart
    private String cartId;

    // Payment amount
    private Double amount;

    // Status: INITIATED | PAID | FAILED | CANCELED
    private String status;

    // Provider: DUMMY
    private String provider;

    // Metadata
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
    public boolean isValid(EntityMetadata metadata) {
        return paymentId != null && !paymentId.isBlank() && 
               cartId != null && !cartId.isBlank() && 
               amount != null && status != null && !status.isBlank();
    }
}

