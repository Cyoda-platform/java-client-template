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
    private String email;
    private Map<String, String> metadata;
    private String requestedEndpoint;
    private Map<String, String> requestedParameters;
    private DigestFormat digestFormat;
    private Status status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

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
        if (id == null || email == null || email.isEmpty() || status == null || digestFormat == null) {
            return false;
        }
        return true;
    }

    public enum DigestFormat {
        PLAIN_TEXT,
        HTML,
        ATTACHMENT
    }

    public enum Status {
        RECEIVED,
        PROCESSING,
        SENT,
        FAILED
    }
}
