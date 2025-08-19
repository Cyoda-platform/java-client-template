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

    private String jobId; // orchestration technical id
    private String jobType; // INGESTION | WEEKLY_REPORT | RECOMMENDATION_RUN
    private String schedule; // optional cron or schedule descriptor
    private String status; // PENDING | RUNNING | COMPLETED | FAILED
    private String parameters; // JSON serialized parameters
    private String startedAt; // timestamp
    private String finishedAt; // timestamp

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
        return jobId != null && !jobId.isBlank()
            && jobType != null && !jobType.isBlank()
            && status != null && !status.isBlank();
    }
}
