package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = "User";

    private String userId;
    private String name;
    private String email;
    private String role;
    private LocalDateTime createdAt;

    public User() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return userId != null && !userId.isBlank()
            && name != null && !name.isBlank()
            && email != null && !email.isBlank()
            && role != null && !role.isBlank();
    }
}
