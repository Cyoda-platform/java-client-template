package com.java_template.application.entity.dataanalysisreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class DataAnalysisReport implements CyodaEntity {
    public static final String ENTITY_NAME = "DataAnalysisReport";
    public static final Integer ENTITY_VERSION = 1;

    private String jobId;
    private String summaryStatistics;
    private String trendAnalysis;
    private String status;
    private String createdAt;

    public DataAnalysisReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return jobId != null && !jobId.isBlank()
                && status != null && !status.isBlank()
                && createdAt != null && !createdAt.isBlank();
    }
}
