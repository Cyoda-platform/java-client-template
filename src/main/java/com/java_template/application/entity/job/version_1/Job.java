package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;

    // number of attempts performed for this job
    private Integer attemptCount;
    // ISO-8601 timestamp strings
    private String createdAt;
    private String startedAt;
    private String completedAt;
    // arbitrary payload for the job (structured)
    private Payload payload;
    // reference to a result entity (serialized UUID or technical id)
    private String resultRef;
    // status and type represented as strings (enums stored as String)
    private String status;
    private String type;

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // createdAt must be present
        if (createdAt == null || createdAt.isBlank()) {
            return false;
        }
        // type and status must be present
        if (type == null || type.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        // attemptCount must be non-null and non-negative
        if (attemptCount == null || attemptCount < 0) {
            return false;
        }
        // payload must be present and valid
        if (payload == null) {
            return false;
        }
        if (payload.getApiUrl() == null || payload.getApiUrl().isBlank()) {
            return false;
        }
        if (payload.getRows() == null || payload.getRows() < 0) {
            return false;
        }
        // startedAt/completedAt/resultRef may be null, no additional checks
        return true;
    }

    @Data
    public static class Payload {
        private String apiUrl;
        private Integer rows;
    }
}