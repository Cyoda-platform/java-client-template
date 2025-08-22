package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    // Unique identifier for the job (serialized UUID or technical id)
    private String jobId;

    // Source URI from which data was ingested
    private String source;

    // ISO-8601 timestamp when ingestion started
    private String startedAt;

    // ISO-8601 timestamp when ingestion completed (optional)
    private String completedAt;

    // Summary of any error that occurred (optional)
    private String errorSummary;

    // Number of records imported
    private Integer importedCount;

    // Current status of the ingestion job (use String rather than enum)
    private String status;

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
        // Validate required string fields using isBlank (and guard against null)
        if (jobId == null || jobId.isBlank()) return false;
        if (source == null || source.isBlank()) return false;
        if (startedAt == null || startedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // importedCount must be present and non-negative
        if (importedCount == null || importedCount < 0) return false;

        // completedAt and errorSummary are optional; no further checks
        return true;
    }
}