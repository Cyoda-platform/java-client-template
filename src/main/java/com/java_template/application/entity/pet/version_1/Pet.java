package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pet entity for the Purrfect Pets application
 * Based on Swagger Petstore API specification
 * 
 * Represents a pet in the pet store with category, name, photos, tags, and status
 */
@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique pet ID
    private String petId;
    
    // Required fields from Swagger Petstore API
    private String name;
    private List<String> photoUrls;
    
    // Optional fields
    private Category category;
    private List<Tag> tags;
    private String status; // available, pending, sold
    
    // Additional business fields
    private String description;
    private Double price;
    private String breed;
    private Integer age;
    private String color;
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
        // Validate required fields: petId, name, and photoUrls
        return petId != null && !petId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               photoUrls != null && !photoUrls.isEmpty();
    }

    /**
     * Nested class for pet category information
     * Represents the category/type of pet (e.g., Dog, Cat, Bird)
     */
    @Data
    public static class Category {
        private Long id;
        private String name;
    }

    /**
     * Nested class for pet tags
     * Represents tags/labels associated with the pet
     */
    @Data
    public static class Tag {
        private Long id;
        private String name;
    }
}
