package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;

@Data
public class PerformanceReport implements CyodaEntity {
    private String jobTechnicalId;
    private LocalDateTime generatedAt;
    private String summary;
    private String reportFileUrl;

    public PerformanceReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("performanceReport");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "performanceReport");
    }

    @Override
    public boolean isValid() {
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) return false;
        if (generatedAt == null) return false;
        if (summary == null || summary.isBlank()) return false;
        if (reportFileUrl == null || reportFileUrl.isBlank()) return false;
        return true;
    }
}
