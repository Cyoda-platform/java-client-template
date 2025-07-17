package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.Instant;
import java.util.Map;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class ReportJob implements CyodaEntity {
    private String reportId;
    private String status;
    private Instant requestedAt;
    private Instant completedAt;
    private Map<String, Object> analysisResult;

    public ReportJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("reportJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "reportJob");
    }

    @Override
    public boolean isValid() {
        return reportId != null && !reportId.isBlank() &&
               status != null && !status.isBlank() &&
               requestedAt != null;
    }
}