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
    private String id;
    private String name;
    private String species; // use String for enum-like field
    private String status;  // use String for enum-like field
    private Integer ageMonths;
    private String breed;
    private Metadata metadata;

    public Pet() {}

    @Data
    public static class Metadata {
        private String enrichedAt; // ISO-8601 timestamp as String
        private List<String> images;
        private List<String> tags;

        public Metadata() {}
    }

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
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // ageMonths, if provided, must be non-negative
        if (ageMonths != null && ageMonths < 0) return false;

        // metadata fields are optional; if present, validate lists are not null (they may be empty)
        if (metadata != null) {
            if (metadata.getImages() == null) return false;
            if (metadata.getTags() == null) return false;
            // enrichedAt can be null/blank (optional), so no strict check here
        }

        return true;
    }
}