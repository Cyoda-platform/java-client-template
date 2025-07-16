package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    private String adoptionId;
    private String petId;
    private String userId;
    private String status;

    public AdoptionRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("adoptionRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "adoptionRequest");
    }

    @Override
    public boolean isValid() {
        if (petId == null || petId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (status == null || !(status.equalsIgnoreCase("pending") || status.equalsIgnoreCase("approved") || status.equalsIgnoreCase("denied"))) return false;
        return true;
    }
}
