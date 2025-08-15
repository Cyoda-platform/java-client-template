package com.java_template.application.entity.job.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String name; // human name of the ingestion job
    private String sourceUrl; // OpenDataSoft feed endpoint
    private String schedule; // cron-like schedule or manual flag
    private String transformRules; // rules or filter expression for normalization
    private String status; // current job status
    private String lastRunAt; // timestamp of last run
    private String resultSummary; // created/updated/skipped counts
    private Integer retryCount; // retry attempts counter
    private String createdBy; // operator who created the job
    private String createdAt; // timestamp

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
        if (this.name == null || this.name.isBlank()) return false;
        if (this.sourceUrl == null || this.sourceUrl.isBlank()) return false;
        if (this.schedule == null || this.schedule.isBlank()) return false;
        // transformRules may be optional
        if (this.createdBy == null || this.createdBy.isBlank()) return false;
        return true;
    }
}
