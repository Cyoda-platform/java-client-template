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

    private String activity_id; // source id from Fakerest
    private String user_id; // owner of activity (serialized UUID)
    private String timestamp; // event time
    private String activity_type; // type of event
    private String payload; // raw event payload serialized as JSON
    private String ingestion_status; // RAW/VALIDATED/DEDUPE

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
        if (activity_id == null || activity_id.isBlank()) return false;
        if (user_id == null || user_id.isBlank()) return false;
        if (timestamp == null || timestamp.isBlank()) return false;
        if (activity_type == null || activity_type.isBlank()) return false;
        if (ingestion_status == null || ingestion_status.isBlank()) return false;
        return true;
    }
}