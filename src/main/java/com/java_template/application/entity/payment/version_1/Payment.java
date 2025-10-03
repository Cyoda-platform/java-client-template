package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Payment entity representing dummy payment processing with
 * automatic approval after 3 seconds for demo purposes.
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core fields
    private String paymentId; // required, unique business identifier
    private String cartId; // required, reference to cart
    private Double amount; // required, payment amount
    private String status; // required: "INITIATED" | "PAID" | "FAILED" | "CANCELED"
    private String provider; // required: "DUMMY" for demo

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
               amount != null && amount > 0.0 &&
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
