package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class FunFact implements CyodaEntity {
    public static final String ENTITY_NAME = "FunFact";

    private String petCategory;
    private String factText;
    private String createdDate;

    public FunFact() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (factText == null || factText.isBlank()) return false;
        if (petCategory == null || petCategory.isBlank()) return false;
        if (createdDate == null || createdDate.isBlank()) return false;
        return true;
    }
}
