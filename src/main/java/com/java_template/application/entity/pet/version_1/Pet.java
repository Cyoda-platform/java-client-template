package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet"; 
    public static final Integer ENTITY_VERSION = 1;

    // Entity fields derived from requirements
    private String id; // technical id (e.g., "pet_555")
    private String name;
    private Integer age;
    private String breed;
    private String color;
    private String description;
    private String gender; // use String for enum-like values
    private String location;
    private List<String> photos;
    private SourceMetadata sourceMetadata;
    private String species;
    private String status;
    private List<String> tags;

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
        // Basic validation: required string fields must be non-blank
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        // Age, if present, must be non-negative
        if (age != null && age < 0) return false;
        // Validate sourceMetadata if present
        if (sourceMetadata != null) {
            if (sourceMetadata.getExternalId() == null || sourceMetadata.getExternalId().isBlank()) {
                return false;
            }
            if (sourceMetadata.getSource() == null || sourceMetadata.getSource().isBlank()) {
                return false;
            }
        }
        return true;
    }

    @Data
    public static class SourceMetadata {
        private String externalId; // maps to external_id
        private Map<String, Object> raw;
        private String source;
    }
}