package com.java_template.application.entity.ingestionrun.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class IngestionRun implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionRun";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String runId; // user-supplied job id
    private String scheduledAt; // scheduled time (ISO timestamp)
    private String startedAt; // actual start (ISO timestamp)
    private String finishedAt; // end time (ISO timestamp)
    private String status; // PENDING/IN_PROGRESS/COMPLETED/FAILED/PARTIAL
    private Long recordsFetched;
    private Long recordsStored;
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
        // require runId and scheduledAt to be present
        if (this.runId == null || this.runId.isBlank()) return false;
        if (this.scheduledAt == null || this.scheduledAt.isBlank()) return false;
        return true;
    }
}
