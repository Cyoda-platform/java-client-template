package com.java_template.application.entity.lookupjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class LookupJob implements CyodaEntity {
    public static final String ENTITY_NAME = "LookupJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Integer userId; // requested ReqRes user id
    // legacy/compat field - status may be present in older models
    private String status; // PENDING | VALIDATING | IN_PROGRESS | SUCCESS | NOT_FOUND | INVALID_INPUT | ERROR | COMPLETED
    // canonical lifecycle field used by workflows
    private String lifecycleState; // PENDING | VALIDATING | IN_PROGRESS | FETCHED | SUCCESS | NOT_FOUND | INVALID_INPUT | ERROR | COMPLETED
    private Integer attempts; // retry count
    private String createdAt; // ISO8601 timestamp
    private String startedAt; // ISO8601
    private String lastAttemptAt; // ISO8601 (optional)
    private String completedAt; // ISO8601
    private String resultRef; // technicalId of persisted User or ErrorEvent
    private String outcome; // SUCCESS | NOT_FOUND | INVALID_INPUT | ERROR
    // free-form metadata as JSON string (optional)
    private String metadata;
    // fetchResponse attached to the job as JSON string for processor-to-processor handoff
    private String fetchResponse;

    public LookupJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields: userId must be present and positive; lifecycle/state must be non-blank; attempts non-null and >= 0
        if (this.userId == null || this.userId <= 0) return false;
        String state = this.lifecycleState != null ? this.lifecycleState : this.status;
        if (state == null || state.isBlank()) return false;
        if (this.attempts == null || this.attempts < 0) return false;
        return true;
    }
}
