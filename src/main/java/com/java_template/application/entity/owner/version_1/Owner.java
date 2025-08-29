package com.java_template.application.entity.owner.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Owner implements CyodaEntity {
    public static final String ENTITY_NAME = "Owner";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Use String for IDs and foreign keys (serialized UUIDs)
    private String id;
    private String fullName;
    private String contactEmail;
    private String contactPhone;
    private List<String> adoptedPetIds;
    private Boolean verified;

    public Owner() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (id == null || id.isBlank()) return false;
        if (fullName == null || fullName.isBlank()) return false;
        if (contactEmail == null || contactEmail.isBlank()) return false;

        // If adoptedPetIds provided, ensure entries are non-blank
        if (adoptedPetIds != null) {
            for (String petId : adoptedPetIds) {
                if (petId == null || petId.isBlank()) return false;
            }
        }
        return true;
    }
}