package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String name; // name of the job
    private String type; // job type (e.g., synchronization, import, export)
    private String targetEntity; // target business entity name (Contact, Lead, Opportunity)
    private String payload; // serialized payload or parameters
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED
    private String createdBy; // user who created the job
    private String scheduleCron; // optional cron expression
    private Integer attemptCount; // retry attempts
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
        if (name == null || name.isBlank()) return false;
        if (targetEntity == null || targetEntity.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (attemptCount != null && attemptCount < 0) return false;
        return true;
    }
}
