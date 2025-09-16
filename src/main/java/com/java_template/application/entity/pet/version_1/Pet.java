package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pet Entity - Represents pets available in the store
 * 
 * This entity manages pet information including availability status,
 * pricing, and characteristics. State is managed automatically by
 * the workflow system via entity.meta.state.
 */
@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String petId;
    
    // Required core business fields
    private String name;
    private String categoryId;
    private Double price;

    // Optional fields for additional business data
    private List<String> photoUrls;
    private List<Tag> tags;
    private String breed;
    private Integer age; // in months
    private String description;
    private Double weight; // in kg
    private String color;
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
        // Validate required fields according to functional requirements
        return petId != null && !petId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               categoryId != null && !categoryId.trim().isEmpty() &&
               price != null && price > 0;
    }

    /**
     * Nested class for pet tags
     * Contains tag information associated with the pet
     */
    @Data
    public static class Tag {
        private String id;
        private String name;
    }
}
