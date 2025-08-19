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

    private String source_url;      // URL to download London houses CSV
    private String scheduled_at;    // ISO8601 datetime or null for immediate
    private String triggered_by;    // user or system
    private String status;          // current job status
    private String created_at;      // when job was created
    private String technicalId;     // datastore id returned by POST

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
        // source_url and triggered_by are required for job creation
        if (source_url == null || source_url.isBlank()) return false;
        if (triggered_by == null || triggered_by.isBlank()) return false;
        return true;
    }
}
