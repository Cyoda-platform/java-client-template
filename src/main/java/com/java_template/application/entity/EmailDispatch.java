package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;

@Data
public class EmailDispatch implements CyodaEntity {
    private String digestRequestId; // FK to DigestRequest
    private String emailContent;
    private LocalDateTime sentAt;
    private String status; // PENDING, SENT, FAILED

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
        if (digestRequestId == null || digestRequestId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // emailContent can be blank or null initially
        return true;
    }
}
