package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestData implements CyodaEntity {
    private String id;
    private String digestRequestId;
    private String dataPayload;
    private java.time.Instant createdAt;

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
        return digestRequestId != null && !digestRequestId.isEmpty() && dataPayload != null;
    }
}
