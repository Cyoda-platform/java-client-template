package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields based on prototype
    private String jobId; // business identifier (serialized UUID or custom id)
    private String name;
    private String status; // use String for enum-like values (e.g., "COMPLETED")
    private String createdAt; // ISO-8601 timestamp as String
    private String lastRunAt; // ISO-8601 timestamp as String
    private Map<String, Object> parameters; // arbitrary parameters map
    private String resultSummary;
    private Integer retryCount;
    private String schedule; // cron expression as String
    private String sourceEndpoint; // URL as String

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
        // Validate required string fields using isBlank()
        if (jobId == null || jobId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (sourceEndpoint == null || sourceEndpoint.isBlank()) return false;

        // Parameters must be present (can be empty map)
        if (parameters == null) return false;

        // retryCount must be non-null and non-negative
        if (retryCount == null || retryCount < 0) return false;

        // createdAt and lastRunAt are optional; schedule and resultSummary are optional
        return true;
    }
}