package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;
import java.time.Instant;

@Data
public class DigestRequest implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String userEmail;
    private String requestDetails; // JSON serialized as String
    private Instant receivedAt;

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
        if (id == null || id.isBlank()) return false;
        if (userEmail == null || userEmail.isBlank()) return false;
        return true;
    }
}
