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

    // Business-facing fields (camelCase) expected by processors/criteria
    private String technicalId; // internal technical id (UUID as string)
    private String activityId; // source id from Fakerest
    private String userId; // owner of activity (serialized UUID)
    private String timestamp; // event time
    private String activityType; // type of event
    private Map<String, Object> payload; // raw event payload as map
    private String ingestionStatus; // RAW/VALIDATED/DEDUPE/PROCESSED/FAILED
    private String dedupeKey; // computed dedupe key
    private String normalizedAt; // timestamp when normalized
    private String sourceJobId; // reference to job that ingested this

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
        if (activityType == null || activityType.isBlank()) return false;
        if (ingestionStatus == null || ingestionStatus.isBlank()) return false;
        return true;
    }
}