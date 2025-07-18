package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;
import java.time.Instant;

@Data
public class DigestJob implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String userEmail;
    private String requestMetadata; // JSON serialized as String
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private Instant createdAt;
    private Instant updatedAt;

    public DigestJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestJob");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (userEmail == null || userEmail.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
