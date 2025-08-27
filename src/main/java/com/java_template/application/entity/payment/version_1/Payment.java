package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = "Payment"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical/Business ID for the payment (serialized UUID or string id)
    private String paymentId;
    // Reference to Cart entity (serialized UUID)
    private String cartId;
    // Payment amount
    private Double amount;
    // Payment provider (use String for enum-like values)
    private String provider;
    // Payment status (use String for enum-like values)
    private String status;
    // Timestamps as ISO-8601 strings
    private String createdAt;
    private String updatedAt;

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
        // Validate required string fields (use isBlank checks)
        if (paymentId == null || paymentId.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (provider == null || provider.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // Validate amount
        if (amount == null) return false;
        if (amount < 0.0) return false;
        return true;
    }
}