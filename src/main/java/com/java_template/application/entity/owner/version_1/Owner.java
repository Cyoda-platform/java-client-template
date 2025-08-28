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
    private String ownerId; // external serialized UUID or id from source
    private String name;
    private String address;
    private String role; // use String for enum-like values
    private String verificationStatus;
    private List<String> savedPets; // references to Pet by serialized id
    private List<String> adoptedPets; // references to Pet by serialized id
    private String contactEmail;
    private String contactPhone;

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
        // Required fields: ownerId, name, contactEmail
        if (ownerId == null || ownerId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (contactEmail == null || contactEmail.isBlank()) return false;

        if (savedPets != null) {
            for (String s : savedPets) {
                if (s == null || s.isBlank()) return false;
            }
        }
        if (adoptedPets != null) {
            for (String s : adoptedPets) {
                if (s == null || s.isBlank()) return false;
            }
        }
        return true;
    }
}