package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String name;
    private boolean active;
    private String contactDetails;
    private String contactType;
    private Preferences preferences;
    private String createdAt;
    private String updatedAt;
    private boolean verified;

    public Subscriber() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields
        if (id == null || id.isBlank()) return false;
        if (contactDetails == null || contactDetails.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // preferences is optional but if present validate its contents
        if (preferences != null) {
            if (preferences.getFrequency() == null || preferences.getFrequency().isBlank()) return false;
            if (preferences.getSpecies() == null || preferences.getSpecies().isEmpty()) return false;
            // tags can be empty or null
        }
        return true;
    }

    @Data
    public static class Preferences {
        private String frequency;
        private List<String> species;
        private List<String> tags;
    }
}