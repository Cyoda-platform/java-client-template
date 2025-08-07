package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class CurrencyRateJob implements CyodaEntity {
    public static final String ENTITY_NAME = "CurrencyRateJob";

    private String source; // data provider or API source for currency rates
    private String requestedAt; // ISO timestamp when job was created
    private String status; // job status: PENDING, PROCESSING, COMPLETED, FAILED
    private String details; // optional additional info or error messages

    public CurrencyRateJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return source != null && !source.isBlank()
            && requestedAt != null && !requestedAt.isBlank()
            && status != null && !status.isBlank();
    }
}
