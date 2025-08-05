package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class AdoptionEvent implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionEvent";
    
    private Long petId;
    private String adopterName;
    private String adoptedAt;
    private String story;

    public AdoptionEvent() {}

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
        if (adoptedAt == null || adoptedAt.isBlank()) return false;
        return true;
    }
}
