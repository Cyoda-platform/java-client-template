package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import java.sql.Timestamp;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class AnalysisReport implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String jobId; // reference to DataIngestionJob
    private String summaryStatistics; // store as JSON string
    private String status; // Consider defining StatusEnum elsewhere
    private Timestamp createdAt;

    public AnalysisReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("analysisReport");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "analysisReport");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && jobId != null && !jobId.isBlank()
            && status != null && !status.isBlank();
    }
}
