package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Pet implements CyodaEntity {
    private Long id;
    private String name;
    private String status;
    private String type;
    private String adopterName;
    private boolean adopted;
    private String adoptedAt;

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "pet");
    }

    @Override
    public boolean isValid() {
        if (id == null || id <= 0) return false;
        if (name == null || name.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (adopted && (adopterName == null || adopterName.isBlank())) return false;
        return true;
    }
}}