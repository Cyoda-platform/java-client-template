package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Owner implements CyodaEntity {
    private UUID technicalId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    public Owner() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("owner");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "owner");
    }

    @Override
    public boolean isValid() {
        return firstName != null && !firstName.isEmpty() && lastName != null && !lastName.isEmpty();
    }
}
