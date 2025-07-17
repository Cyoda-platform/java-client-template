package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import java.time.Instant;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class EmailDispatch implements CyodaEntity {
    private UUID id;
    private UUID digestRequestId;
    private String email;
    private Instant sentAt;
    private Status status;
    private String errorMessage;

    public EmailDispatch() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("emailDispatch");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "emailDispatch");
    }

    @Override
    public boolean isValid() {
        if (digestRequestId == null) return false;
        if (email == null || email.isBlank()) return false;
        if (status == null) return false;
        return true;
    }

    public enum Status {
        PENDING, SENT, FAILED
    }
}
