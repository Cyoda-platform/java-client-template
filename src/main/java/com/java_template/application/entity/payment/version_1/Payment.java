package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = "PAYMENT";
    public static final Integer ENTITY_VERSION = 1;

    @JsonProperty("paymentId")
    private String paymentId;

    @JsonProperty("cartId")
    private String cartId;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

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
               provider != null && !provider.trim().isEmpty();
    }
}
