package com.java_template.application.entity.hackernewsitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class HackerNewsItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HackerNewsItem";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Long id; // Hacker News item id as in Firebase JSON, required
    private String type; // item type from HN JSON, required
    private String originalJson; // the full JSON payload as received, stored as JSON string
    private String importTimestamp; // ISO8601 UTC, added when accepted
    private String status; // processing status: PENDING/ENRICHED/FAILED, freeform

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
        // Validate required fields id and type
        if (id == null) return false;
        if (type == null || type.isBlank()) return false;
        if (originalJson == null || originalJson.isBlank()) return false;
        return true;
    }
}
