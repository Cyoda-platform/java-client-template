package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class ConversionReport implements CyodaEntity {
    private String jobTechnicalId;
    private LocalDateTime createdTimestamp;
    private BigDecimal btcUsdRate;
    private BigDecimal btcEurRate;
    private LocalDateTime emailSentTimestamp;
    private String status;

    public ConversionReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("conversionReport");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "conversionReport");
    }

    @Override
    public boolean isValid() {
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) {
            return false;
        }
        if (btcUsdRate == null) {
            return false;
        }
        if (btcEurRate == null) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}
