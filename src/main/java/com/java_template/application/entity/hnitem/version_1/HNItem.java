package com.java_template.application.entity.hnitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class HNItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HNItem";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Long id; // Hacker News item id from incoming JSON
    private String type; // item type from incoming JSON
    private String rawJson; // original JSON payload as received
    private String importTimestamp; // ISO8601 timestamp added by system
    private String status; // PENDING VALID INVALID STORED
    private String errorMessage; // validation or processing error if any

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
        // Required fields per workflow: id and type
        if (this.id == null) return false;
        if (this.type == null || this.type.isBlank()) return false;
        return true;
    }
}
