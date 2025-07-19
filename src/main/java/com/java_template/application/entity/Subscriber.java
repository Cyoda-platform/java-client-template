package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;

@Data
public class Subscriber implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String email;
    private StatusEnum status;

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
        if (id == null || id.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (status == null) return false;
        return true;
    }

    public enum StatusEnum {
        ACTIVE,
        INACTIVE
    }
}
