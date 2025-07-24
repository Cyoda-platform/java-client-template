package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Subscriber implements CyodaEntity {
    private String email;
    private LocalDateTime subscriptionDate;
    private String status; // ACTIVE, INACTIVE

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
        return email != null && !email.isBlank() && status != null && !status.isBlank();
    }
}
