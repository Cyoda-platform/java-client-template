package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import java.sql.Timestamp;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class EmailDispatchLog implements CyodaEntity {
    private UUID id;
    private UUID digestRequestId;
    private String email;
    private DispatchStatus dispatchStatus;
    private Timestamp sentAt;
    private String errorMessage;

    public EmailDispatchLog() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("emailDispatchLog");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "emailDispatchLog");
    }

    @Override
    public boolean isValid() {
        if (id == null || digestRequestId == null || email == null || email.isEmpty() || dispatchStatus == null) {
            return false;
        }
        return true;
    }

    public enum DispatchStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
