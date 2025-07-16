package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Pet implements CyodaEntity {

    private java.util.UUID technicalId;
    private String name;
    private String type;
    private String status;
    private String description;

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "pet");
    }

    @Override
    public boolean isValid() {
        // Validate that the name, type, and status are not null or empty
        return name != null && !name.isBlank()
                && type != null && !type.isBlank()
                && status != null && !status.isBlank();
    }
}
