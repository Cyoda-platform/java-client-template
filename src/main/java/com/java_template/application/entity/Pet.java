package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;

@Data
public class Pet implements CyodaEntity {

    private UUID technicalId;
    private String name;
    private String type;
    private String status;
    private String[] tags;

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(Integer.parseInt("1"));
        return new OperationSpecification.Entity(modelSpec, "pet");
    }

    @Override
    public boolean isValid() {
        // Validate that name, type, and status are not null or empty
        if (name == null || name.isEmpty()) return false;
        if (type == null || type.isEmpty()) return false;
        if (status == null || status.isEmpty()) return false;
        // technicalId can be null (not assigned yet), tags can be null or empty
        return true;
    }
}
