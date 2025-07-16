package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PetSupplementary implements CyodaEntity {
    private String petId;
    private String info;
    private long createdAt;

    public PetSupplementary() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petSupplementary");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petSupplementary");
    }

    @Override
    public boolean isValid() {
        // Validate petId is not null (can be empty string) and info is not null or blank
        return petId != null && info != null && !info.isBlank();
    }
}
