package com.example.application.entity.example_entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
/**
 * Supplementary entity
 */
@Data
public class OtherEntity implements CyodaEntity {
    public static final String ENTITY_NAME = OtherEntity.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required core business fields
    private String name;


    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return true;
    }
}
