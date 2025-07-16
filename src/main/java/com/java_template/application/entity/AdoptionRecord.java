package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRecord implements CyodaEntity {
    private Long petId;
    private String adopterName;
    private String adoptedAt;

    public AdoptionRecord() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("adoptionRecord");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "adoptionRecord");
    }

    @Override
    public boolean isValid() {
        if (petId == null || petId <= 0) return false;
        if (adopterName == null || adopterName.isBlank()) return false;
        if (adoptedAt == null || adoptedAt.isBlank()) return false;
        return true;
    }
}