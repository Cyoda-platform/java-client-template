package com.java_template.application.entity.salessnapshot.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class SalesSnapshot implements CyodaEntity {
    public static final String ENTITY_NAME = "SalesSnapshot";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String timestamp; // snapshot timestamp
    private Integer quantity; // quantity sold in snapshot
    private Double revenue; // revenue for this snapshot

    public SalesSnapshot() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (timestamp == null || timestamp.isBlank()) return false;
        if (quantity == null) return false;
        if (revenue == null) return false;
        return true;
    }
}