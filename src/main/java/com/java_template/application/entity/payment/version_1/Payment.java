package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = "Payment"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on requirements example
    private String paymentId; // technical id (e.g., "pay-123")
    private BigDecimal amount; // payment amount
    private String cartId; // foreign key reference to Cart (serialized UUID/string)
    private String provider; // payment provider (e.g., "DUMMY")
    private String status; // payment status (e.g., "PAID")
    private String createdAt; // ISO timestamp string
    private String updatedAt; // ISO timestamp string

    public Payment() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (paymentId == null || paymentId.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (provider == null || provider.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Validate amount
        if (amount == null) return false;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return false;

        // updatedAt can be optional, but if present must not be blank
        if (updatedAt != null && updatedAt.isBlank()) return false;

        return true;
    }
}