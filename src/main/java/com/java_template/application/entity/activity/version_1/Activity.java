package com.java_template.application.entity.activity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Activity implements CyodaEntity {
    public static final String ENTITY_NAME = "Activity";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId;
    private String activityId; // external API id
    private String userId; // id of user from external system
    private String timestamp; // ISO datetime of activity
    private String activityType; // type/category of activity
    private Map<String, Object> rawPayload; // raw JSON from external API
    private Boolean validated; // validation result
    private Map<String, Object> enriched; // derived metadata e.g., duration, tags
    private Double classificationScore; // confidence of classification
    private String ingestionJobTechnicalId; // references IngestionJob technicalId
    private String persistedAt; // ISO datetime when stored

    public Activity() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank
        if (activityId == null || activityId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (timestamp == null || timestamp.isBlank()) return false;
        if (ingestionJobTechnicalId == null || ingestionJobTechnicalId.isBlank()) return false;
        return true;
    }
}
