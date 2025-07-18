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
    private String id;
    private UUID technicalId;
    private String petId; // unique pet identifier
    private String name; // pet's name
    private String category; // e.g., "Cat", "Dog"
    private String status; // PetStatusEnum (AVAILABLE, PENDING, SOLD)
    private List<String> tags; // descriptive tags
    private List<String> photoUrls; // links to pet images

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
