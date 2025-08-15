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

    private String id; // domain identifier
    private String technicalId; // datastore-specific identifier returned by POST endpoints
    private String name; // human-friendly job name
    private String scheduleCron; // cron expression or schedule specification
    private String sourceEndpoint; // data source URL
    private Integer limit; // limit parameter for pagination
    private String status; // current workflow state
    private String startedAt; // ISO timestamp
    private String finishedAt; // ISO timestamp
    private String resultSummary; // summary of ingestion outcome
    private String errorDetails; // error messages / stack traces
    private String createdBy; // user or system that created the job
    private String createdAt; // ISO timestamp
    private String updatedAt; // ISO timestamp

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
        // Basic validation: name and scheduleCron are required for a Job to be valid
        if (this.name == null || this.name.isBlank()) return false;
        if (this.scheduleCron == null || this.scheduleCron.isBlank()) return false;
        return true;
    }
}
