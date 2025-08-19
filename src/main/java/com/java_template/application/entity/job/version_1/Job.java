package com.java_template.application.entity.job.version_1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String jobType; // INGEST_FACTS or DELIVER_WEEKLY or OTHER
    private ObjectNode parameters; // job-specific params e.g., sources, batchSize, globalSendDay
    private String scheduledAt; // ISO timestamp
    private String status; // PENDING/RUNNING/COMPLETED/FAILED
    private String createdAt; // ISO timestamp
    private ObjectNode resultSummary; // counts: processed, failed, sent
    private Map<String, Integer> retriesPolicy; // maxRetries: Integer

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
        return id != null && !id.isBlank()
            && jobType != null && !jobType.isBlank();
    }
}
