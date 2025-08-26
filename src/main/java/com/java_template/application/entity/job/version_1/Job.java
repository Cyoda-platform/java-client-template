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
    private String id; // technical id (serialized UUID or simple string)
    private String apiEndpoint;
    private Integer attempts;
    private String createdAt;
    private String startedAt;
    private String finishedAt;
    private String lastError;
    private String schedule;
    private String state; // use String for enum-like values

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
        // id must be present
        if (id == null || id.isBlank()) return false;
        // endpoint required
        if (apiEndpoint == null || apiEndpoint.isBlank()) return false;
        // attempts must be non-null and non-negative
        if (attempts == null || attempts < 0) return false;
        // createdAt required
        if (createdAt == null || createdAt.isBlank()) return false;
        // schedule required (cron expression)
        if (schedule == null || schedule.isBlank()) return false;
        // state required
        if (state == null || state.isBlank()) return false;
        // lastError, startedAt, finishedAt can be null/blank depending on job lifecycle
        return true;
    }
}