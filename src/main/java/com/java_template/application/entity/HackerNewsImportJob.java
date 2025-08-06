package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HackerNewsImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "HackerNewsImportJob";
    private String technicalId; // auto-generated unique identifier for persistence and retrieval
    private String importTimestamp; // ISO 8601 format, timestamp when the item was imported
    private String rawJson; // original JSON of the Hacker News item as received
    private String state; // VALID or INVALID depending on presence of required fields
    private Long id; // id field from the Hacker News item, required
    private String type; // type field from the Hacker News item, required

    public HackerNewsImportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields: id and type
        if (id == null) return false;
        if (type == null || type.isBlank()) return false;
        return true;
    }
}
