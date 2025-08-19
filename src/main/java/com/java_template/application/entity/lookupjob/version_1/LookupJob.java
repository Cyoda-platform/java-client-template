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
    private String status; // PENDING | VALIDATING | IN_PROGRESS | SUCCESS | NOT_FOUND | INVALID_INPUT | ERROR | COMPLETED
    private Integer attempts; // retry count
    private String createdAt; // ISO8601 timestamp
    private String startedAt; // ISO8601
    private String completedAt; // ISO8601
    private String resultRef; // technicalId of persisted User or ErrorEvent

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
        // Validate required fields: userId must be present and positive; status must be non-blank; attempts non-null and >= 0
        if (this.userId == null || this.userId <= 0) return false;
        if (this.status == null || this.status.isBlank()) return false;
        if (this.attempts == null || this.attempts < 0) return false;
        return true;
    }
}
