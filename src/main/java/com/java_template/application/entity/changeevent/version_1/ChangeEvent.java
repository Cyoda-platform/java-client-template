package com.java_template.application.entity.changeevent.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ChangeEvent implements CyodaEntity {
    public static final String ENTITY_NAME = "ChangeEvent";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id;
    private String laureateId; // foreign reference to Laureate.id
    private String eventType; // new or updated
    private String payload; // serialized payload (JSON)
    private String createdAt; // timestamp

    public ChangeEvent() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required: id, laureateId, eventType
        if (this.id == null || this.id.isBlank()) return false;
        if (this.laureateId == null || this.laureateId.isBlank()) return false;
        if (this.eventType == null || this.eventType.isBlank()) return false;
        return true;
    }
}
