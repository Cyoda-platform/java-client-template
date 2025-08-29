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
    private String id; // technical id (serialized UUID or system id)
    private String name;
    private Integer ageMonths;
    private String breed;
    private String species; // use String for enum-like values
    private String status; // use String for enum-like values (e.g., RESERVED)
    private String ownerUserId; // foreign key reference as serialized UUID
    private String healthNotes;

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
        // name, species and status are required
        if (name == null || name.isBlank()) {
            return false;
        }
        if (species == null || species.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        // ageMonths if provided must be non-negative
        if (ageMonths != null && ageMonths < 0) {
            return false;
        }
        // if provided, id and ownerUserId must not be blank
        if (id != null && id.isBlank()) {
            return false;
        }
        if (ownerUserId != null && ownerUserId.isBlank()) {
            return false;
        }
        return true;
    }
}