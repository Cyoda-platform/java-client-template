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
    private Integer age;
    private String bio;
    private String breed;
    private String gender;
    private String location;
    private List<String> photos;
    private String species;
    private String status;
    private List<AdoptionRequest> adoptionRequests;

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
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (age == null || age < 0) return false;
        if (adoptionRequests != null) {
            for (AdoptionRequest req : adoptionRequests) {
                if (req == null || !req.isValid()) return false;
            }
        }
        return true;
    }

    @Data
    public static class AdoptionRequest {
        private String notes;
        private String ownerId; // serialized UUID reference to Owner
        private String requestedAt; // ISO-8601 timestamp as String
        private String status;

        public boolean isValid() {
            if (ownerId == null || ownerId.isBlank()) return false;
            if (status == null || status.isBlank()) return false;
            if (requestedAt == null || requestedAt.isBlank()) return false;
            // notes can be optional
            return true;
        }
    }
}