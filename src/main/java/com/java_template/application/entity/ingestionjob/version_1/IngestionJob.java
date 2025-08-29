package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical id assigned by the system (serialized UUID as String)
    private String id;

    // Natural/business identifier for the ingestion job
    private String jobId;

    // Comma separated list of data formats, e.g. "JSON,XML"
    private String dataFormats;

    // ISO-8601 timestamp of the last run, e.g. "2025-08-25T09:00:00Z"
    private String lastRunAt;

    // Notification email for job run events
    private String notifyEmail;

    // Cron expression for scheduling, e.g. "0 9 * * MON"
    private String scheduleCron;

    // Source URL to ingest from
    private String sourceUrl;

    // Job status, use String for enum-like values (e.g., "COMPLETED", "FAILED")
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
        // Required fields: jobId, sourceUrl, status must be non-blank
        if (jobId == null || jobId.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // If optional string fields are provided they must not be blank
        if (scheduleCron != null && scheduleCron.isBlank()) return false;
        if (notifyEmail != null && notifyEmail.isBlank()) return false;
        if (dataFormats != null && dataFormats.isBlank()) return false;
        if (lastRunAt != null && lastRunAt.isBlank()) return false;

        return true;
    }
}