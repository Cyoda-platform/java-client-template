package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * CatFact Entity - Represents a cat fact retrieved from the Cat Fact API
 * 
 * This entity stores:
 * - Fact text from the API
 * - Retrieval timestamp
 * - Campaign ID for tracking which weekly campaign this fact belongs to
 */
@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = "CatFact";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier - fact ID from API
    private String factId;

    // Core business fields
    private String text;
    private LocalDateTime retrievedAt;
    private String campaignId; // Links to the weekly campaign

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
        return factId != null && !factId.isBlank() && text != null && !text.isBlank();
    }
}

