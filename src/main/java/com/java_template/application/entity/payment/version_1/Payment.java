package com.java_template.application.entity.payment.version_1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private String paymentId;
    private String cartId;
    private Double amount;
    private String provider;
    private Instant createdAt;
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
               "DUMMY".equals(provider);
    }
}
