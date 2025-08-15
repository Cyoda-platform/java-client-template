package com.java_template.application.entity.importtask.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;

@Data
public class ImportTask implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportTask";
    public static final Integer ENTITY_VERSION = 1;

    // Fields as defined in the functional requirements
    private String jobTechnicalId; // reference to the ImportJob technicalId that created this task
    private Long hnItemId; // the Hacker News id if it could be parsed
    private String status; // QUEUED, PROCESSING, SUCCEEDED, FAILED
    private Integer attempts; // number of processing attempts performed
    private String errorMessage; // short description if processing failed
    private Instant createdAt;
    private Instant lastUpdatedAt;

    public ImportTask() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: jobTechnicalId must be present to associate the task with a job
        if (this.jobTechnicalId == null || this.jobTechnicalId.isBlank()) return false;
        return true;
    }
}
