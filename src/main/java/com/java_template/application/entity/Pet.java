package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Pet implements CyodaEntity {
    private UUID technicalId;
    private Integer id;
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
        // Validate that id is not null and name, type, status are not blank
        if (id == null) return false;
        if (name == null || name.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
