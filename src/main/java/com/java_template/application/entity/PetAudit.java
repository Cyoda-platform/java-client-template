package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PetAudit implements CyodaEntity {
    private String petName;
    private long timestamp;
    private String status;

    public PetAudit() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petAudit");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petAudit");
    }

    @Override
    public boolean isValid() {
        if (petName == null || petName.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (timestamp <= 0) return false;
        return true;
    }
}
