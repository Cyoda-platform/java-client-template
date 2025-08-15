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

    private Long id; // business id from system or source if applicable
    private String technicalId; // datastore-imitation technical identifier returned by POST endpoints
    private String sourceUrl; // API or data source
    private String scheduledTime; // ISO-8601 datetime when job is scheduled
    private String startTime; // ISO-8601 datetime when ingestion started
    private String endTime; // ISO-8601 datetime when ingestion finished
    private String status; // current lifecycle state e.g., SCHEDULED, INGESTING, SUCCEEDED, FAILED, NOTIFIED_SUBSCRIBERS
    private Integer fetchedRecordCount; // number of laureate records fetched
    private Integer succeededCount; // number of records successfully processed
    private Integer failedCount; // number of records failed during processing
    private String errorDetails; // detailed error / stacktrace if failure
    private String config; // JSON blob with job-specific configuration like limit, filters

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
        // technicalId and sourceUrl are required for persisted jobs that will be processed.
        if (this.technicalId == null || this.technicalId.isBlank()) return false;
        if (this.sourceUrl == null || this.sourceUrl.isBlank()) return false;
        // status should be present for lifecycle tracking
        if (this.status == null || this.status.isBlank()) return false;
        return true;
    }
}
