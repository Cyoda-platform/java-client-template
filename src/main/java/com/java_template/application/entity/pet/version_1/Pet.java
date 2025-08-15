package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String petId; // external ID from Petstore API or source system; business identifier
    private String name; // pet name
    private String species; // e.g., cat, dog
    private String breed; // breed description
    private Integer age; // age in years
    private String status; // availability status; e.g., available, reserved, adopted
    private String location; // shelter or store location
    private String ownerExternalId; // external owner id if present in source
    private String ownerTechnicalId; // datastore technicalId of associated Owner, nullable (serialized UUID as String)
    private String source; // data source identifier, e.g., Petstore API
    private String lastSyncedAt; // ISO-8601 timestamp when this entity was last synced
    private String createdAt; // ISO-8601
    private String updatedAt; // ISO-8601

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
        // Required fields: petId, name, species
        if(this.petId == null || this.petId.isBlank()) return false;
        if(this.name == null || this.name.isBlank()) return false;
        if(this.species == null || this.species.isBlank()) return false;
        return true;
    }
}
