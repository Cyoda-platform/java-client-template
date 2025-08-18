package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;

    // Orchestration fields
    private String id; // technical or business id
    private String name; // job name
    private String type; // e.g., PETSTORE_SYNC
    private String schedule; // cron or schedule expression
    private String lastRunAt; // ISO timestamp
    private String nextRunAt; // ISO timestamp
    private String status; // scheduled, running, completed, failed
    private String payload; // serialized payload (JSON)
    private String createdAt; // ISO timestamp

    // Additional fields expected by processors
    private Integer version;

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
        // Basic validation: name, type and schedule are required for orchestration jobs
        if (name == null || name.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (schedule == null || schedule.isBlank()) return false;
        return true;
    }

    // Compatibility convenience methods
    public String getTechnicalId() { return this.id; }
    public void setTechnicalId(String technicalId) { this.id = technicalId; }
}