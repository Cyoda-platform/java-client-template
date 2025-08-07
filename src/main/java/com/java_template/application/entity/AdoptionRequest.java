package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest";

    private String adopterName;
    private String adopterContact;
    private String petId;
    private String requestDate;
    private String status;

    public AdoptionRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (adopterName == null || adopterName.isBlank()) return false;
        if (adopterContact == null || adopterContact.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (requestDate == null || requestDate.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
