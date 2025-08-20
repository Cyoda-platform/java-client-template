package com.java_template.application.entity.activity.version_1; // replace {entityName} with actual entity name in lowercase

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

    private String activityId; // source activity id
    private String userId; // user identifier
    private String timestamp; // ISO timestamp of activity
    private String type; // activity type / name
    private Map<String, Object> metadata; // free-form activity details
    private String source; // origin system
    private Boolean processed; // whether downstream analysis completed
    private Boolean anomalyFlag; // true if flagged

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
        if (activityId == null || activityId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (timestamp == null || timestamp.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        return true;
    }
}
