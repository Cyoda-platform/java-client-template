package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;

@Data
public class DigestRequest implements CyodaEntity {
    private String email;
    private String requestMetadata;
    private LocalDateTime createdAt;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

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
        if (email == null || email.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // requestMetadata can be blank or null
        return true;
    }
}
