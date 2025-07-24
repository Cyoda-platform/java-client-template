package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Report implements CyodaEntity {
    private String jobTechnicalId;
    private LocalDateTime generatedAt;
    private Double btcUsdRate;
    private Double btcEurRate;
    private Boolean emailSent;

    public Report() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("report");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "report");
    }

    @Override
    public boolean isValid() {
        // jobTechnicalId must not be blank
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) return false;
        // generatedAt must not be null
        if (generatedAt == null) return false;
        // btcUsdRate and btcEurRate must not be null
        if (btcUsdRate == null || btcEurRate == null) return false;
        // emailSent must not be null
        if (emailSent == null) return false;
        return true;
    }
}
