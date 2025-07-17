package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.Map;
import java.util.UUID;
import java.sql.Timestamp;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestRequest implements CyodaEntity {
    private UUID id;
    private String userEmail;
    private Map<String, Object> metadata;
    private Timestamp requestedAt;
    private Status status;

    public DigestRequest() {}

    public enum Status {
        CREATED, PROCESSING, SENT, FAILED
    }

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestRequest");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestRequest");
    }

    @Override
    public boolean isValid() {
        return id != null && userEmail != null && !userEmail.isBlank() && requestedAt != null && status != null;
    }
}
