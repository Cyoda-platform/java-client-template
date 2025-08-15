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

    private Long id; // Hacker News item identifier from Firebase HN API JSON
    private String type; // Hacker News item type from Firebase HN API JSON
    private String originalJson; // the complete original JSON object as string
    private String importTimestamp; // ISO-8601 UTC timestamp when the item was imported
    private String state; // VALID or INVALID
    private String createdAt; // ISO-8601 UTC timestamp when record persisted
    private String updatedAt; // ISO-8601 UTC timestamp when last updated

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
        // Validate mandatory fields for HNItem: id and type must be present
        if (this.id == null) return false;
        if (this.type == null || this.type.isBlank()) return false;
        return true;
    }
}
