package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import java.sql.Timestamp;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestData implements CyodaEntity {
    private UUID id;
    private UUID digestRequestId;
    private String rawData;
    private Timestamp processedAt;

    public DigestData() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestData");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestData");
    }

    @Override
    public boolean isValid() {
        return id != null && digestRequestId != null && rawData != null && !rawData.isBlank();
    }
}
