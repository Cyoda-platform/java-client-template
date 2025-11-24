package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: CatFact entity represents a cat fact retrieved from the Cat Fact API.
 * Stores the fact content, source, and retrieval metadata.
 */
@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = CatFact.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String factId;
    
    // Required core business fields
    private String content;
    private LocalDateTime retrievedDate;
    
    // Optional fields for additional business data
    private String source;
    private String apiId;
    private Integer length;
    private LocalDateTime publishedDate;

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
        return factId != null && !factId.trim().isEmpty() &&
               content != null && !content.trim().isEmpty();
    }
}

