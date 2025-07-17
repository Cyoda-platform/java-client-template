package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;

@Data
public class Notification implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String type; // email, SMS
    private String recipientId;
    private String message;
    private String status;

    public Notification() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("notification");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "notification");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
                && type != null && !type.isBlank()
                && recipientId != null && !recipientId.isBlank()
                && message != null && !message.isBlank()
                && status != null && !status.isBlank();
    }
}
