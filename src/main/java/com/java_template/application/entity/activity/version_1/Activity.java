package com.java_template.application.entity.activity.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Activity implements CyodaEntity {
    public static final String ENTITY_NAME = "Activity";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String activityId; // external activity id
    private String userId; // link to User (serialized UUID)
    private String type; // activity type
    private String startTime; // ISO timestamp
    private String endTime; // ISO timestamp
    private Long durationSec; // computed
    private String sourceFetchedAt; // ISO timestamp
    private String dedupHint; // fingerprint
    private Boolean anomalyFlag; // true if anomalous

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
        // require activityId, userId, type, startTime, endTime
        if (this.activityId == null || this.activityId.isBlank()) return false;
        if (this.userId == null || this.userId.isBlank()) return false;
        if (this.type == null || this.type.isBlank()) return false;
        if (this.startTime == null || this.startTime.isBlank()) return false;
        if (this.endTime == null || this.endTime.isBlank()) return false;
        return true;
    }
}
