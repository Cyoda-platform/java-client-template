package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class EmailDispatchRecord implements CyodaEntity {
    private String jobTechnicalId;
    private String email;
    private String dispatchStatus;
    private String sentAt;

    public EmailDispatchRecord() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("emailDispatchRecord");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "emailDispatchRecord");
    }

    @Override
    public boolean isValid() {
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) {
            return false;
        }
        if (email == null || email.isBlank()) {
            return false;
        }
        if (dispatchStatus == null || dispatchStatus.isBlank()) {
            return false;
        }
        return true;
    }
}
