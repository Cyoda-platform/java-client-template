package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String jobName; // friendly name for the ingestion run
    private String scheduleDate; // ISO datetime when job was scheduled to run
    private String windowStart; // ISO datetime start of ingestion window
    private String windowEnd; // ISO datetime end of ingestion window
    private String status; // PENDING/IN_PROGRESS/COMPLETED/FAILED
    private String createdAt; // ISO datetime
    private String startedAt; // ISO datetime
    private String finishedAt; // ISO datetime
    private Map<String, Object> summary; // counts, errors, metrics

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
        // Validate required string fields using isBlank
        if (this.jobName == null || this.jobName.isBlank()) return false;
        if (this.scheduleDate == null || this.scheduleDate.isBlank()) return false;
        if (this.windowStart == null || this.windowStart.isBlank()) return false;
        if (this.windowEnd == null || this.windowEnd.isBlank()) return false;
        if (this.status == null || this.status.isBlank()) return false;
        return true;
    }
}
