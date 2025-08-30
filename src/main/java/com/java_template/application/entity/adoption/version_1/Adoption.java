package com.java_template.application.entity.adoption.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Adoption implements CyodaEntity {
    public static final String ENTITY_NAME = "Adoption"; 
    public static final Integer ENTITY_VERSION = 1;

    private String id; // serialized UUID
    private String ownerId; // serialized UUID for Owner
    private String petId; // serialized UUID for Pet
    private String adoptionDate; // adoption date in String format
    private String status; // adoption status

    public Adoption() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() &&
               ownerId != null && !ownerId.isBlank() &&
               petId != null && !petId.isBlank() &&
               adoptionDate != null && !adoptionDate.isBlank() &&
               status != null && !status.isBlank();
    }
}