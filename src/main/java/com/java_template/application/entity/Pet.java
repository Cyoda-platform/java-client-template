package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;
import java.util.UUID;

@Data
public class Pet implements CyodaEntity {
    private Long id; // business ID
    private UUID technicalId; // database ID
    private String name;
    private String category;
    private List<String> photoUrls;
    private List<String> tags;
    private String status; // available, pending, sold

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
        return id != null
                && name != null && !name.isBlank()
                && category != null && !category.isBlank()
                && status != null && !status.isBlank();
    }
}
