package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class User implements CyodaEntity {

    private int id;
    private String email;
    private String firstName;
    private String lastName;
    private String avatar;

    public User() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("user");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "user");
    }

    @Override
    public boolean isValid() {
        return id > 0 && email != null && !email.isEmpty() && firstName != null && !firstName.isEmpty()
                && lastName != null && !lastName.isEmpty() && avatar != null && !avatar.isEmpty();
    }
}
