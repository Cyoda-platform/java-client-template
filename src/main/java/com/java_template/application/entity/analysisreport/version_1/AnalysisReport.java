package com.java_template.application.entity.analysisreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AnalysisReport implements CyodaEntity {
    public static final String ENTITY_NAME = "AnalysisReport";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String job_technicalId; // link to originating DataIngestJob
    private String generated_at;    // when analysis finished
    private String summary_metrics; // JSON string of computed metrics
    private Integer record_count;   // rows analyzed
    private String report_link;     // location of report artifact
    private String status;          // ready, failed, archived
    private String technicalId;     // datastore id returned by POST

    public AnalysisReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // job_technicalId and generated_at are required
        if (job_technicalId == null || job_technicalId.isBlank()) return false;
        if (generated_at == null || generated_at.isBlank()) return false;
        if (summary_metrics == null || summary_metrics.isBlank()) return false;
        return true;
    }
}
