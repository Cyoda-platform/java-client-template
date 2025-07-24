package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;

@Data
public class Pet implements CyodaEntity {
    private Long petId; // Petstore API pet identifier
    private String name; // pet's name
    private String category; // category of the pet, e.g., "Cat", "Dog"
    private List<String> photoUrls; // URLs of pet photos
    private List<String> tags; // additional tags or fun nicknames
    private String status; // entity lifecycle state (available, pending, sold)

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
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
