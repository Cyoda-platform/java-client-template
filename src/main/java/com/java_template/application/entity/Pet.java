package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class Pet implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String petId; // unique identifier for the pet
    private String name; // pet's name
    private String type; // species, e.g., cat, dog
    private String breed; // breed of the pet
    private Integer age; // age in years
    private String availabilityStatus; // AVAILABLE, ADOPTED, PENDING
    private String status; // NEW, ACTIVE, ARCHIVED

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
        return petId != null && !petId.isBlank() &&
                name != null && !name.isBlank() &&
                type != null && !type.isBlank() &&
                breed != null && !breed.isBlank() &&
                age != null && age >= 0 &&
                availabilityStatus != null && !availabilityStatus.isBlank() &&
                status != null && !status.isBlank();
    }
}
