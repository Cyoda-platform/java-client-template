package com.java_template.application.entity.hnitem.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class HNItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HNItem";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Long id; // Hacker News id field, required for validation
    private String type; // Hacker News type field, required for validation
    private Object rawJson; // the original JSON payload as received, preserved exactly
    private String importTimestamp; // ISO timestamp added when persisted
    private Object metadata; // optional, e.g., validation notes, source

    public HNItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // id and type are required
        if (this.id == null) return false;
        if (this.type == null || this.type.isBlank()) return false;
        return true;
    }
}
