package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Subscriber implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String email;
    private String status; // Use SubscriptionStatusEnum in real code
    private LocalDateTime subscribedAt;

    public Subscriber() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("subscriber");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "subscriber");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && email != null && !email.isBlank() && status != null && !status.isBlank();
    }
}
