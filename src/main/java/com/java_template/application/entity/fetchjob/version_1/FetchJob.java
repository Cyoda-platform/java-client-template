package com.java_template.application.entity.fetchjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class FetchJob implements CyodaEntity {
    public static final String ENTITY_NAME = "FetchJob";
    public static final Integer ENTITY_VERSION = 1;
    // Orchestration entity fields
    private String requestDate; // YYYY-MM-DD
    private String scheduledTime; // e.g., 18:00Z
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private String startedAt; // ISO-8601 timestamp
    private String completedAt; // ISO-8601 timestamp
    private Integer fetchedCount;
    private Integer failedCount;
    private String responsePayload; // raw response JSON as String
    private String errorMessage;

    public FetchJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // requestDate and scheduledTime are required for a FetchJob
        return requestDate != null && !requestDate.isBlank()
            && scheduledTime != null && !scheduledTime.isBlank();
    }
}
