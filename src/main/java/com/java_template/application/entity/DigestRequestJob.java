package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;

@Data
public class DigestRequestJob implements CyodaEntity {
    private String email;
    private String metadata;
    private String status;
    private LocalDateTime createdAt;

    public DigestRequestJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestRequestJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestRequestJob");
    }

    @Override
    public boolean isValid() {
        return email != null && !email.isBlank() && metadata != null && !metadata.isBlank();
    }
}
