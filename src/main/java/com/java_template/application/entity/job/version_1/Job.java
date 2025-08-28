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
    // Technical identifier (returned by POST endpoints)
    private String id;
    // Source URL to fetch data from
    private String sourceUrl;
    // Cron schedule expression
    private String schedule;
    // State as string (e.g., "SCHEDULED")
    private String state;
    // Number of processed records
    private Integer processedCount;
    // Number of failed records
    private Integer failedCount;
    // Summary of errors if any
    private String errorSummary;
    // ISO-8601 timestamps as strings (nullable)
    private String startedAt;
    private String finishedAt;

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
        // Required string fields must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (schedule == null || schedule.isBlank()) return false;
        if (state == null || state.isBlank()) return false;

        // Counts must be present and non-negative
        if (processedCount == null || processedCount < 0) return false;
        if (failedCount == null || failedCount < 0) return false;

        // Optional string timestamp fields: if provided, must not be blank
        if (startedAt != null && startedAt.isBlank()) return false;
        if (finishedAt != null && finishedAt.isBlank()) return false;

        // errorSummary can be null or blank
        return true;
    }
}