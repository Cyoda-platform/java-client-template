package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pet Entity - Represents a pet available in the store
 * 
 * This entity manages pets with their details, category, and availability status.
 * The status field from the Petstore API is replaced by the entity.meta.state system.
 * 
 * States: available, pending, sold
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
    private Category category;
    private List<Tag> tags;
    private String description;
    private Double price;
    private LocalDate birthDate;
    private String breed;
    private Double weight;
    private Boolean vaccinated;
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
               photoUrls != null;
    }

    /**
     * Nested class for category information
     */
    @Data
    public static class Category {
        private String categoryId;
        private String name; // e.g., "Dogs", "Cats", "Birds"
    }

    /**
     * Nested class for tag information
     */
    @Data
    public static class Tag {
        private String tagId;
        private String name; // e.g., "friendly", "trained", "young"
    }
}
