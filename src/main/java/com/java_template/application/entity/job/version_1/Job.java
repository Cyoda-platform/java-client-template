package com.java_template.application.entity.job.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String schedule_date; // date for the run, e.g. 2025-08-21
    private String timezone; // timezone for schedule
    private String status; // PENDING/IN_PROGRESS/COMPLETED/FAILED
    private String created_by; // user or system
    private String parameters; // serialized JSON object (ingestion window, Fakerest endpoints, retry_policy)
    private String started_at; // timestamp
    private String completed_at; // timestamp

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
        if (schedule_date == null || schedule_date.isBlank()) return false;
        if (timezone == null || timezone.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (created_by == null || created_by.isBlank()) return false;
        if (parameters == null || parameters.isBlank()) return false;
        return true;
    }
}