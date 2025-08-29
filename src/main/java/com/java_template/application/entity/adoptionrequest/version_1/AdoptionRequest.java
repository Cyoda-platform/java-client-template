package com.java_template.application.entity.adoptionrequest.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class AdoptionRequest implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionRequest"; 
    public static final Integer ENTITY_VERSION = 1;

    private String id; // Technical ID
    private String petId; // Foreign key reference to Pet
    private String requestDate; // Date of request
    private String status; // Status of the request
    private String userId; // Foreign key reference to User

    public AdoptionRequest() {} 

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
               requestDate != null && !requestDate.isBlank() && 
               status != null && !status.isBlank() && 
               userId != null && !userId.isBlank();
    }
}