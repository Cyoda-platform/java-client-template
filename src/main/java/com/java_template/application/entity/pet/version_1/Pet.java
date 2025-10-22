package com.java_template.application.entity.pet.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Pet entity representing a pet loaded from an external pet API.
 * Implements CyodaEntity interface for Cyoda workflow integration.
 */
@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = Pet.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String petId;

    // Core pet information
    private String name;
    private String species;
    private String breed;
    private Integer age;
    private String color;
    private Double weight;

    // Pet status and metadata
    private String status;
    private String description;
    private LocalDateTime loadedAt;
    private LocalDateTime updatedAt;

    // External API reference
    private String externalApiId;
    private String externalApiSource;

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
               species != null && !species.trim().isEmpty();
    }
}

