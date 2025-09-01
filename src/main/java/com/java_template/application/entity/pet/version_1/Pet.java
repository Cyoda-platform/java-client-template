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
    private String id; // serialized UUID
    private String name;
    private Integer age;
    private String breed;
    private List<String> photoUrls;
    private String sourceUrl;
    private String species;
    private String status; // use String for enums
    private List<String> vaccinations;

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
        // Basic required field checks
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Age, if provided, must be non-negative
        if (age != null && age < 0) return false;

        // If photoUrls provided, none should be blank
        if (photoUrls != null) {
            for (String url : photoUrls) {
                if (url == null || url.isBlank()) return false;
            }
        }

        // If vaccinations provided, none should be blank
        if (vaccinations != null) {
            for (String v : vaccinations) {
                if (v == null || v.isBlank()) return false;
            }
        }

        // sourceUrl, if present, must not be blank
        if (sourceUrl != null && sourceUrl.isBlank()) return false;

        // breed may be null/blank (optional)
        return true;
    }
}