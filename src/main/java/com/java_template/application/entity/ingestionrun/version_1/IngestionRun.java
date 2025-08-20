package com.java_template.application.entity.ingestionrun.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class IngestionRun implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionRun";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // user-supplied job id
    private String runId;
    // scheduled time (ISO timestamp)
    private String scheduledAt;
    // actual start time (ISO timestamp)
    private String startedAt;
    // end time (ISO timestamp)
    private String finishedAt;
    // status: PENDING/IN_PROGRESS/COMPLETED/FAILED/PARTIAL
    private String status;
    // counts
    private Integer recordsFetched;
    private Integer recordsStored;
    // summary of errors if any
    private String errorsSummary;

    public IngestionRun() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required fields: runId and scheduledAt must be present and not blank
        if (this.runId == null || this.runId.isBlank()) return false;
        if (this.scheduledAt == null || this.scheduledAt.isBlank()) return false;
        return true;
    }
}
