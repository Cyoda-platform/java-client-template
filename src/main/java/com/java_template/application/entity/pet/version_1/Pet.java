package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: Pet entity represents animals available for adoption or purchase in the pet store
 * with categorization, photos, tags, and availability status managed through workflow states.
 */
@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String petId;
    
    // Required core business fields
    private String name;
    private List<String> photoUrls;

    // Optional fields for additional business data
    private PetCategory category;
    private List<PetTag> tags;
    private String description;
    private String breed;
    private Integer age; // in months
    private Double price;
    private Double weight; // in kg
    private String color;
    private String gender; // "male", "female", "unknown"
    private Boolean vaccinated;
    private Boolean neutered;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return petId != null && !petId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               photoUrls != null && !photoUrls.isEmpty() &&
               (gender == null || gender.equals("male") || gender.equals("female") || gender.equals("unknown")) &&
               (price == null || price > 0) &&
               (age == null || age > 0) &&
               (weight == null || weight > 0);
    }

    /**
     * Nested class for pet category information
     */
    @Data
    public static class PetCategory {
        private String categoryId;
        private String categoryName;
    }

    /**
     * Nested class for pet tags
     */
    @Data
    public static class PetTag {
        private String tagId;
        private String tagName;
    }
}
