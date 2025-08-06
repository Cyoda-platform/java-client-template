package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class HackerNewsItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HackerNewsItem";

    private Long id; // unique Hacker News item id
    private String type; // item type, e.g., story, comment, job
    private String jsonData; // original JSON from Firebase HN API
    private String state; // VALID or INVALID, based on presence of id and type
    private String importTimestamp; // ISO 8601 format timestamp of import, stored separately from jsonData

    public HackerNewsItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate that id and type are present
        return id != null && type != null && !type.isBlank();
    }
}
