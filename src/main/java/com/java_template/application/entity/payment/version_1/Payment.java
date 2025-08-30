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
    private String paymentId; // technical id for the payment
    private String cartId; // foreign key reference to Cart (serialized UUID / string)
    private Double amount;
    private String provider; // e.g., "DUMMY"
    private String status; // e.g., "INITIATED"
    private String createdAt; // ISO timestamp as String
    private String updatedAt; // ISO timestamp as String

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
        // Validate required string fields using isBlank to catch empty/whitespace
        if (paymentId == null || paymentId.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (provider == null || provider.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Validate amount
        if (amount == null) return false;
        if (amount < 0) return false; // negative amounts are invalid

        return true;
    }
}