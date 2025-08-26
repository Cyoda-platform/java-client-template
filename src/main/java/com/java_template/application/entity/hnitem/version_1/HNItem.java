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
    // Hacker News numeric id
    private Long id;
    // ISO-8601 timestamp when imported
    private String importTimestamp;
    // Serialized original JSON payload from Hacker News
    private String originalJson;
    // Status such as STORED, PENDING, etc.
    private String status;
    // Type of HN item: story, comment, job, etc.
    private String type;

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
        if (id == null || id <= 0) return false;
        if (importTimestamp == null || importTimestamp.isBlank()) return false;
        if (originalJson == null || originalJson.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        return true;
    }
}