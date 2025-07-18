package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DigestRequest implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String userId;
    private LocalDateTime requestTime;
    private String parameters; // criteria for digest, could be JSON string

    public DigestRequest() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestRequest");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() &&
               userId != null && !userId.isBlank() &&
               parameters != null && !parameters.isBlank();
    }
}
