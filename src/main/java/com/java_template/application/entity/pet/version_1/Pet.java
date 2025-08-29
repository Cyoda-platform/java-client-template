package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;
    
    private String id; // Serialized UUID for the pet
    private String name; // Name of the pet
    private Integer age; // Age of the pet
    private String type; // Type of the pet (e.g., Cat, Dog)
    private String status; // Status of the pet (e.g., Available, Adopted)

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
        return id != null && !id.isBlank() && 
               name != null && !name.isBlank() && 
               age != null && age >= 0 && 
               type != null && !type.isBlank() && 
               status != null && !status.isBlank();
    }
}