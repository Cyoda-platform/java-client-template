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

    private String jobTechnicalId; // link to originating DataIngestJob (serialized UUID)
    private String generatedAt; // ISO8601 datetime when analysis finished
    private String summaryMetrics; // JSON string of computed metrics, aggregates, distributions
    private Integer recordCount; // rows analyzed
    private String reportLink; // location of report artifact
    private String status; // ready, failed, archived
    private String technicalId; // datastore id returned by POST

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
        // required fields: jobTechnicalId, generatedAt, summaryMetrics, recordCount, reportLink, status
        if (this.jobTechnicalId == null || this.jobTechnicalId.isBlank()) {
            return false;
        }
        if (this.generatedAt == null || this.generatedAt.isBlank()) {
            return false;
        }
        if (this.summaryMetrics == null || this.summaryMetrics.isBlank()) {
            return false;
        }
        if (this.recordCount == null) {
            return false;
        }
        if (this.reportLink == null || this.reportLink.isBlank()) {
            return false;
        }
        if (this.status == null || this.status.isBlank()) {
            return false;
        }
        return true;
    }
}
