package com.java_template.application.entity.greeting.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Greeting Entity - A simple demo entity for the Hello Greeting workflow
 * 
 * Demonstrates:
 * - Basic entity with timestamp and message fields
 * - CyodaEntity implementation
 * - Workflow-driven state transitions
 */
@Data
public class Greeting implements CyodaEntity {
    public static final String ENTITY_NAME = "Greeting";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier field
    private String greetingId;

    // Core business fields
    private LocalDateTime timestamp;
    private String message;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return greetingId != null && !greetingId.isBlank();
    }
}

