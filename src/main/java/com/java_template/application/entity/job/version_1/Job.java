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

    private String id; // business id
    private String name; // human friendly job name
    private String sourceUrl; // OpenDataSoft dataset endpoint
    private String schedule; // cron or human schedule description
    private String lastRunAt; // timestamp of last run
    private String status; // current job status
    private List<String> runHistory; // records of past runs with results
    private Map<String, String> config; // ingestion/transform rules, rate limits

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
        // Required: schedule and sourceUrl per JobValidationCriterion
        if (this.schedule == null || this.schedule.isBlank()) return false;
        if (this.sourceUrl == null || this.sourceUrl.isBlank()) return false;
        return true;
    }
}
