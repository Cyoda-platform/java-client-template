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
    private String species;
    private String breed;
    private String sex; // use String for enum-like values (e.g., "M", "F")
    private Integer ageMonths;
    private List<String> images;
    private String location;
    private String status;
    private String vaccinationSummary;
    private String medicalNotes;
    private String addedAt; // ISO-8601 timestamp as String

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
        // Required string fields
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Optional string fields if present should not be blank
        if (breed != null && breed.isBlank()) return false;
        if (sex != null && sex.isBlank()) return false;
        if (location != null && location.isBlank()) return false;
        if (vaccinationSummary != null && vaccinationSummary.isBlank()) return false;
        if (medicalNotes != null && medicalNotes.isBlank()) return false;
        if (addedAt != null && addedAt.isBlank()) return false;

        // Numeric validations
        if (ageMonths != null && ageMonths < 0) return false;

        // Images list validation: if present, entries should be non-blank
        if (images != null) {
            for (String img : images) {
                if (img == null || img.isBlank()) return false;
            }
        }

        return true;
    }
}