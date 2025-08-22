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
    private String id; // serialized UUID or technical id
    private String name;
    private Integer ageMonths;
    private String breed;
    private String gender;
    private String species;
    private String status;
    private String sourceOrigin;
    private String createdAt; // ISO timestamp as String
    private List<String> photos;
    private TechnicalMetadata technicalMetadata;

    @Data
    public static class TechnicalMetadata {
        private String enrichedAt;
        private String ingestedByJob;
        private String sourceRecordId;
    }

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
        // Required string fields must be non-null and non-blank
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Numeric checks
        if (ageMonths != null && ageMonths < 0) return false;

        // photos list entries must not be blank if provided
        if (photos != null) {
            for (String p : photos) {
                if (p == null || p.isBlank()) return false;
            }
        }

        // technical metadata fields, if present, must not be blank strings
        if (technicalMetadata != null) {
            if (technicalMetadata.getEnrichedAt() != null && technicalMetadata.getEnrichedAt().isBlank())
                return false;
            if (technicalMetadata.getIngestedByJob() != null && technicalMetadata.getIngestedByJob().isBlank())
                return false;
            if (technicalMetadata.getSourceRecordId() != null && technicalMetadata.getSourceRecordId().isBlank())
                return false;
        }

        return true;
    }
}