package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Report implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String reportJobId; // foreign key reference
    private BigDecimal btcUsdRate;
    private BigDecimal btcEurRate;
    private OffsetDateTime timestamp;

    public Report() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (reportJobId == null || reportJobId.isBlank()) return false;
        if (btcUsdRate == null) return false;
        if (btcEurRate == null) return false;
        if (timestamp == null) return false;
        return true;
    }
}
