package com.java_template.application.entity.category.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Category Entity - Represents pet categories for organization
 * 
 * This entity manages category information for organizing pets.
 * State is managed automatically by the workflow system via
 * entity.meta.state (active, inactive).
 */
@Data
public class Category implements CyodaEntity {
    public static final String ENTITY_NAME = Category.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String categoryId;
    
    // Required core business fields
    private String name;

    // Optional fields for additional business data
    private String description;
    private String imageUrl;
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
        return categoryId != null && !categoryId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty();
    }
}
