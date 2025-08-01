package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.Map;

@Data
public class CustomerProfileUpdate implements CyodaEntity {
    public static final String ENTITY_NAME = "CustomerProfileUpdate";

    private String customerId;
    private Map<String, String> updatedFields;
    private String updatedAt;
    private String updatedBy;

    public CustomerProfileUpdate() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return customerId != null && !customerId.isBlank()
            && updatedFields != null && !updatedFields.isEmpty()
            && updatedAt != null && !updatedAt.isBlank()
            && updatedBy != null && !updatedBy.isBlank();
    }
}
