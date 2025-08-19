package com.java_template.application.entity.dataingestjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class DataIngestJob implements CyodaEntity {
    public static final String ENTITY_NAME = "DataIngestJob";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String sourceUrl; // URL to download London houses CSV
    private String scheduledAt; // ISO8601 datetime or null
    private String triggeredBy; // user or system
    private String status; // current job status
    private String createdAt; // when job was created
    private String technicalId; // datastore id returned by POST

    public DataIngestJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // sourceUrl and triggeredBy are required for a valid job
        if (this.sourceUrl == null || this.sourceUrl.isBlank()) {
            return false;
        }
        if (this.triggeredBy == null || this.triggeredBy.isBlank()) {
            return false;
        }
        return true;
    }
}
