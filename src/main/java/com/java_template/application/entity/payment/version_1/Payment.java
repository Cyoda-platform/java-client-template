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

    private String paymentId; // payment provider id if any
    private String orderId; // serialized UUID reference to Order
    private String method; // card, wallet, etc.
    private Double amount;
    private String currency;
    private String status; // workflow-driven state
    private String providerResponse; // serialized JSON of provider result
    private String createdAt; // ISO timestamp

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
        if (orderId == null || orderId.isBlank()) return false;
        if (method == null || method.isBlank()) return false;
        if (amount == null || amount < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        return true;
    }
}
