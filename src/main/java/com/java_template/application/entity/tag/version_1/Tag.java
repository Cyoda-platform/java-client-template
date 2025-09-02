package com.java_template.application.entity.tag.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

@Data
public class Tag implements CyodaEntity {
    public static final String ENTITY_NAME = Tag.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private Long id;
    private String name;
    private String color;
    private String description;
    private Boolean active;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() && 
               name.length() >= 1 && name.length() <= 30;
    }
}
