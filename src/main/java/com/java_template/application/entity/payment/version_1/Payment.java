package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Objects;

@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = "Payment";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private Double amount;
    private String cartId; // foreign key reference (serialized UUID)
    private String createdAt; // ISO timestamp
    private String paymentId; // technical id
    private String provider;
    private String status;
    private String updatedAt; // ISO timestamp

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
        // Validate required string fields using isBlank (and null-check to avoid NPE)
        if (paymentId == null || paymentId.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (provider == null || provider.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Validate numeric fields
        if (amount == null || amount <= 0.0) return false;

        // createdAt/updatedAt can be optional, but if provided should not be blank
        if (createdAt != null && createdAt.isBlank()) return false;
        if (updatedAt != null && updatedAt.isBlank()) return false;

        return true;
    }
}