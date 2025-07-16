package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Pet implements CyodaEntity {
    private java.util.UUID technicalId; // internal id from entityService
    private String petId; // string form of technicalId
    private String name;
    private String category;
    private String status;
    private String description;

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(Integer.parseInt("" + ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "pet");
    }

    @Override
    public boolean isValid() {
        // Validate required fields: name, category, status should not be blank
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // Validate status pattern
        if (!(status.equals("available") || status.equals("pending") || status.equals("sold"))) return false;
        return true;
    }
}
