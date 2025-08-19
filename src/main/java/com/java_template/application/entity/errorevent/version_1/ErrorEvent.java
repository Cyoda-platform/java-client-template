package com.java_template.application.entity.errorevent.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ErrorEvent implements CyodaEntity {
    public static final String ENTITY_NAME = "ErrorEvent";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Integer code; // HTTP or app error code
    private String message; // user friendly message
    private String details; // optional debug/info
    private String occurredAt; // ISO8601
    private String relatedJobId; // LookupJob technicalId

    public ErrorEvent() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields: code non-null and >=0, message non-blank, occurredAt non-blank, relatedJobId non-blank
        if (this.code == null || this.code < 0) return false;
        if (this.message == null || this.message.isBlank()) return false;
        if (this.occurredAt == null || this.occurredAt.isBlank()) return false;
        if (this.relatedJobId == null || this.relatedJobId.isBlank()) return false;
        return true;
    }
}
