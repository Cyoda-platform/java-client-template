package com.java_template.application.entity.payment.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = "Payment";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id;
    private String orderId; // serialized UUID
    private Double amount;
    private String currency;
    private String method;
    private String status; // e.g., AUTHORIZED, CAPTURED
    private String transactionId;
    private String createdAt;

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
        if (id == null || id.isBlank()) return false;
        if (orderId == null || orderId.isBlank()) return false;
        if (amount == null || amount < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
