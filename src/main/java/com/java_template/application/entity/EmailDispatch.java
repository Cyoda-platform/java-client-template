package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.Instant;
import java.util.UUID;

@Data
public class EmailDispatch implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String digestRequestId;
    private String emailStatus;
    private Instant sentAt;

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
        return id != null && !id.isBlank()
            && digestRequestId != null && !digestRequestId.isBlank()
            && emailStatus != null && !emailStatus.isBlank();
    }
}
