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

    private String id; // business id from Petstore or internal
    private String name; // pet name
    private String species; // dog, cat, etc.
    private String breed; // breed info
    private Integer age; // years or months
    private String status; // available, pending, adopted
    private List<String> photos; // list of image metadata (serialized)
    private String description; // short bio
    private String healthNotes; // vaccines, conditions
    private String sourceId; // original Petstore id

    // Additional fields expected by processors
    private Boolean isArchived;
    private Integer version;

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
        // Required fields: id, name, species, status
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }

    // Compatibility convenience methods used by processors/criteria
    public String getTechnicalId() {
        return this.id;
    }

    public void setTechnicalId(String technicalId) {
        this.id = technicalId;
    }

    // photos setter accepting raw object lists for flexible processors
    @SuppressWarnings("unchecked")
    public void setPhotos(List<?> photos) {
        this.photos = (List<String>) photos;
    }
}