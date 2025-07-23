package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;
import java.util.List;

@Data
public class Pet implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String name;
    private String category;
    private StatusEnum status;
    private List<String> tags;
    private List<String> photoUrls;

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
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (status == null) return false;
        return true;
    }

    public enum StatusEnum {
        AVAILABLE, PENDING, SOLD
    }
}
