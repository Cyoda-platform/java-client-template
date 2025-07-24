package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Pet implements CyodaEntity {
    private String petId;
    private String name;
    private String category;
    private List<String> photoUrls;
    private List<String> tags;
    private String status; // AVAILABLE, PENDING, SOLD

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
        return petId != null && !petId.isBlank()
            && name != null && !name.isBlank()
            && category != null && !category.isBlank()
            && status != null && !status.isBlank();
    }
}
