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
    private String id; // technical id (serialized UUID or similar)
    private String name;
    private String species;
    private String breed;
    private String sex;
    private String size;
    private String age;
    private String bio;
    private String healthNotes;
    private String source;
    private String status;
    private String importedAt; // ISO-8601 timestamp as String
    private List<String> photos;
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
        // Required core fields
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Validate collections if present (no blank entries)
        if (photos != null) {
            for (String p : photos) {
                if (p == null || p.isBlank()) return false;
            }
        }
        if (tags != null) {
            for (String t : tags) {
                if (t == null || t.isBlank()) return false;
            }
        }

        return true;
    }
}