package com.java_template.application.entity.importjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;

@Data
public class ImportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ImportJob";
    public static final Integer ENTITY_VERSION = 1;

    // Fields as defined in the functional requirements
    private String jobName; // human-friendly name for the job
    private String source; // optional identifier of where the payload originated
    private String payload; // the raw Firebase-format Hacker News JSON provided for this job
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED
    private Integer itemsCreatedCount; // number of HackerNewsItem entities created/updated as part of this job
    private Instant createdAt; // when the job was persisted
    private Instant completedAt; // when the job finished processing, if applicable

    public ImportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Basic validation: jobName and payload must be present (non-blank) to consider the job valid for processing
        if (this.jobName == null || this.jobName.isBlank()) return false;
        if (this.payload == null || this.payload.isBlank()) return false;
        return true;
    }
}
