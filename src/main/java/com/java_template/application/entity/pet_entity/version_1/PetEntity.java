package com.java_template.application.entity.pet_entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pet Entity - Represents pet products from the Pet Store API with performance tracking capabilities
 */
@Data
public class PetEntity implements CyodaEntity {
    public static final String ENTITY_NAME = PetEntity.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Core pet data from Pet Store API
    private Long petId;
    private String name;
    private Category category;
    private List<String> photoUrls;
    private List<Tag> tags;
    
    // Business fields for performance tracking
    private Double price;
    private Integer stockLevel;
    private Integer salesVolume;
    private Double revenue;
    private LocalDateTime lastSaleDate;
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
        if (petId == null) {
            return false;
        }
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        if (category == null) {
            return false;
        }
        // Stock level cannot be negative
        if (stockLevel != null && stockLevel < 0) {
            return false;
        }
        // Sales volume and revenue should be non-negative if set
        if (salesVolume != null && salesVolume < 0) {
            return false;
        }
        if (revenue != null && revenue < 0) {
            return false;
        }
        return true;
    }

    /**
     * Nested class for pet category information
     */
    @Data
    public static class Category {
        private Long id;
        private String name;
    }

    /**
     * Nested class for pet tags
     */
    @Data
    public static class Tag {
        private Long id;
        private String name;
    }
}
