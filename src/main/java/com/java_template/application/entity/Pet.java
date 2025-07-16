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
    private String category;
    private java.util.List<String> tags;
    private String status;

    public Pet() {
        this.tags = new java.util.ArrayList<>();
    }

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "pet");
    }

    @Override
    public boolean isValid() {
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (tags == null || tags.size() > 5) return false;
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) return false;
        }
        if (status == null || !status.matches("available|pending|sold")) return false;
        return true;
    }
}
