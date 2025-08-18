package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;

    // Orchestration entity fields
    private String id; // business id
    private String jobType; // e.g., MAIL_SEND_BATCH
    private String payloadId; // reference to associated business entity (e.g., Mail id)
    private String payloadType; // the type of payload referenced (e.g., Mail)
    private String status; // CREATED SCHEDULED RUNNING FAILED COMPLETED
    private String scheduledAt; // timestamp
    private String createdAt; // timestamp
    private Integer attempts; // current attempt count
    private Integer maxAttempts; // maximum allowed attempts
    private String lastError; // last error message

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
        // Required: jobType and payloadId must be present
        if (jobType == null || jobType.isBlank()) return false;
        if (payloadId == null || payloadId.isBlank()) return false;
        return true;
    }
}
