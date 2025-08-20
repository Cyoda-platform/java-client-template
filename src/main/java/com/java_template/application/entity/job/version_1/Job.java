package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;
    // Orchestration job fields
    private String jobId; // business job identifier (serialized UUID as String)
    private String type; // e.g., "bulk_import", "reconciliation"
    private String status; // workflow-driven state
    private String payload; // optional JSON payload
    private String result; // optional JSON result
    private String createdAt; // ISO timestamp
    private String startedAt; // ISO timestamp
    private String finishedAt; // ISO timestamp

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
        if (jobId == null || jobId.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        // status can be empty for newly created jobs
        return true;
    }
}
