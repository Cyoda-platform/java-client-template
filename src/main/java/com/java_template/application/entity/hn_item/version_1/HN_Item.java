package com.java_template.application.entity.hn_item.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class HN_Item implements CyodaEntity {
    public static final String ENTITY_NAME = "HN_Item"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Numeric id from source (example: 123)
    private Long id;
    // ISO timestamp when imported (example: "2025-08-22T12:00:00Z")
    private String importTimestamp;
    // Raw JSON payload as serialized String
    private String rawJson;
    // Type of the item (example: "story")
    private String type;

    public HN_Item() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields: id, importTimestamp, rawJson, type
        if (id == null || id <= 0) return false;
        if (importTimestamp == null || importTimestamp.isBlank()) return false;
        if (rawJson == null || rawJson.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        return true;
    }
}