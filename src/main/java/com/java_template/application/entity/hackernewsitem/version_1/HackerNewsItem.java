package com.java_template.application.entity.hackernewsitem.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class HackerNewsItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HackerNewsItem";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // Hacker News item id (stored as String to preserve original JSON format)
    private String type; // type of the item (e.g., "story", "comment")
    private String originalJson; // the original JSON payload from Firebase HN API
    private Long importTimestamp; // epoch millis when the item was imported

    public HackerNewsItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
                && type != null && !type.isBlank();
    }
}
