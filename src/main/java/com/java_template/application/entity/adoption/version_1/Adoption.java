package com.java_template.application.entity.adoption.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Adoption implements CyodaEntity {
    public static final String ENTITY_NAME = "Adoption"; 
    public static final Integer ENTITY_VERSION = 1;

    private String id; // serialized UUID for adoption ID
    private String petId; // serialized UUID for pet ID
    private String userId; // serialized UUID for user ID
    private String adoptionDate; // date of adoption in String format

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
               petId != null && !petId.isBlank() &&
               userId != null && !userId.isBlank() &&
               adoptionDate != null && !adoptionDate.isBlank();
    }
}