package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId; // platform assigned technical id (optional)
    private String jobName; // Human readable name
    private String scheduledFor; // ISO DateTime string planned start time (kept as string for external inputs)
    private Instant startedAt; // actual start time
    private Instant finishedAt; // actual end
    private Instant lastProcessedAt; // last processing timestamp
    private Integer fetchedCount; // total items fetched
    private Integer newCount; // new cover photos created
    private Integer duplicateCount; // duplicates detected
    private Integer errorCount; // numeric error counter
    private ObjectNode errorSummary; // structured summary of errors
    private String status; // PENDING/RUNNING/COMPLETED/FAILED
    private String initiatedBy; // scheduler/manual user id
    private Map<String, Object> runParameters; // options for this run
    private Instant createdAt; // when job persisted

    public IngestionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (this.jobName == null || this.jobName.isBlank()) return false;
        if (this.status == null || this.status.isBlank()) return false;
        return true;
    }
}
