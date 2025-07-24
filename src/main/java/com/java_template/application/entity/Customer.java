package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Customer implements CyodaEntity {
    private String customerId;
    private String name;
    private String email;
    private String phone;
    private String createdAt;

    public Customer() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("customer");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "customer");
    }

    @Override
    public boolean isValid() {
        if (customerId == null || customerId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (phone == null || phone.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        return true;
    }
}
