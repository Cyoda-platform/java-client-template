package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // business id (serialized UUID)
    private String jobType; // INGEST_FACTS | DELIVER_WEEKLY | OTHER
    private Map<String, Object> parameters; // job-specific params e.g., sources, batchSize, globalSendDay
    private String scheduledAt; // ISO timestamp (optional)
    private String status; // PENDING/RUNNING/COMPLETED/FAILED
    private String createdAt; // ISO timestamp
    private Map<String, Integer> resultSummary; // counts: processed, failed, sent
    private Map<String, Integer> retriesPolicy; // { "maxRetries": Integer }

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
        // id and jobType are required and must not be blank
        if (id == null || id.isBlank()) return false;
        if (jobType == null || jobType.isBlank()) return false;
        // parameters can be null for some jobs, but if present must be a map
        // retriesPolicy when present should contain maxRetries
        if (retriesPolicy != null) {
            Integer max = retriesPolicy.get("maxRetries");
            if (max == null || max < 0) return false;
        }
        return true;
    }
}
