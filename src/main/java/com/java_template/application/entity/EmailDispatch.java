package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import java.sql.Timestamp;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class EmailDispatch implements CyodaEntity {
    private UUID id;
    private UUID digestRequestId;
    private Timestamp emailSentAt;
    private EmailFormat emailFormat;
    private Status status;

    public EmailDispatch() {}

    public enum EmailFormat {
        PLAIN_TEXT, HTML, ATTACHMENT
    }

    public enum Status {
        PENDING, SENT, FAILED
    }

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("emailDispatch");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "emailDispatch");
    }

    @Override
    public boolean isValid() {
        return id != null && digestRequestId != null && emailFormat != null && status != null;
    }
}
