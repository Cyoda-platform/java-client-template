package com.java_template.application.entity.job.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields derived from prototype
    private String id; // technical id, serialized UUID or string identifier
    private String schedule;
    private String state;
    private String errorSummary;
    private String startedAt; // ISO timestamp as string
    private String finishedAt; // ISO timestamp as string
    private Integer recordsFailedCount;
    private Integer recordsFetchedCount;
    private Integer recordsProcessedCount;
    private Integer subscribersNotifiedCount;

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
        // Required string fields must not be blank
        if (id == null || id.isBlank()) return false;
        if (state == null || state.isBlank()) return false;
        if (schedule == null || schedule.isBlank()) return false;

        // Optional string fields if present must not be blank
        if (startedAt != null && startedAt.isBlank()) return false;
        if (finishedAt != null && finishedAt.isBlank()) return false;
        if (errorSummary != null && errorSummary.isBlank()) return false;

        // Numeric counts must be non-negative if present
        if (recordsFailedCount != null && recordsFailedCount < 0) return false;
        if (recordsFetchedCount != null && recordsFetchedCount < 0) return false;
        if (recordsProcessedCount != null && recordsProcessedCount < 0) return false;
        if (subscribersNotifiedCount != null && subscribersNotifiedCount < 0) return false;

        return true;
    }
}