package com.java_template.application.entity.deliveryRecord.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class DeliveryRecord implements CyodaEntity {
    public static final String ENTITY_NAME = "DeliveryRecord";
    public static final Integer ENTITY_VERSION = 1;

    private String id;
    private String mailTechnicalId;
    private String recipientId;
    private String recipientEmail;
    private String status;
    private Integer attempts;
    private String lastAttemptAt;
    private String lastError;
    private String createdAt;
    private String updatedAt;

    public DeliveryRecord() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return this.mailTechnicalId != null && !this.mailTechnicalId.isBlank()
            && this.recipientEmail != null && !this.recipientEmail.isBlank();
    }
}
