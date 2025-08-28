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
    private String errorSummary;
    private Integer failedCount;
    private String finishedAt; // ISO-8601 timestamp as String
    private String jobId; // technical id / serialized UUID
    private String scheduleAt; // ISO-8601 timestamp as String
    private String sourceUrl;
    private String startedAt; // ISO-8601 timestamp as String
    private String state; // enum represented as String
    private Integer succeededCount;
    private Integer totalRecords;

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
        // Validate required string fields
        if (jobId == null || jobId.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (state == null || state.isBlank()) return false;
        if (scheduleAt == null || scheduleAt.isBlank()) return false;

        // Validate numeric counts
        if (failedCount == null || failedCount < 0) return false;
        if (succeededCount == null || succeededCount < 0) return false;
        if (totalRecords == null || totalRecords < 0) return false;

        // Basic consistency: succeeded + failed should not exceed totalRecords
        long sum = (long) succeededCount + (long) failedCount;
        if (sum > totalRecords) return false;

        return true;
    }
}