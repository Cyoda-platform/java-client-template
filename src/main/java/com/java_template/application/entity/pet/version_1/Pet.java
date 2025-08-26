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
    // Add your entity fields here
    // Technical id (serialized UUID or other string id)
    private String id;
    private Integer age;
    private String avatarUrl;
    private String breed;
    private String color;
    private String healthNotes;
    private String name;
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
        // Required string fields must be non-blank
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // Age, if provided, must be non-negative
        if (age != null && age < 0) return false;
        // sourceMetadata is optional, but if present, petstoreId should not be blank
        if (sourceMetadata != null) {
            if (sourceMetadata.getPetstoreId() != null && sourceMetadata.getPetstoreId().isBlank()) return false;
            // raw can be any map, no strict validation
        }
        return true;
    }

    @Data
    public static class SourceMetadata {
        private String petstoreId;
        private Map<String, Object> raw;

        public SourceMetadata() {}
    }
}