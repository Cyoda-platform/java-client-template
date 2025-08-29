package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (serialized UUID or provider id)
    private Integer ageMonths;
    private String availability; // use String for enums
    private String breed;
    private String healthStatus;
    private String ownerId; // foreign key reference as serialized UUID
    private String species;
    private String name;

    public Pet() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required: name, species, availability, ageMonths must be valid
        if (name == null || name.isBlank()) {
            return false;
        }
        if (species == null || species.isBlank()) {
            return false;
        }
        if (availability == null || availability.isBlank()) {
            return false;
        }
        if (ageMonths == null || ageMonths < 0) {
            return false;
        }
        // If ownerId is provided it must not be blank (serialized UUID)
        if (ownerId != null && ownerId.isBlank()) {
            return false;
        }
        // If id is present it must not be blank
        if (id != null && id.isBlank()) {
            return false;
        }
        return true;
    }
}