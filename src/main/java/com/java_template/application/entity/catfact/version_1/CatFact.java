package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * CatFact Entity - Stores cat facts retrieved from external APIs
 * 
 * This entity represents individual cat facts that are retrieved from external APIs
 * and used in weekly email campaigns to subscribers.
 */
@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = CatFact.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String factId;
    
    // Required core business fields
    private String content;
    private String source;
    private LocalDateTime retrievedDate;
    private Boolean isUsed;

    // Optional fields for additional business data
    private String category;
    private Integer contentLength;
    private String language;
    private CatFactMetadata metadata;
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
        return factId != null && !factId.trim().isEmpty() &&
               content != null && !content.trim().isEmpty() &&
               source != null && !source.trim().isEmpty() &&
               retrievedDate != null &&
               isUsed != null;
    }

    /**
     * Nested class for cat fact metadata
     * Stores additional information about the fact source and quality
     */
    @Data
    public static class CatFactMetadata {
        private String apiEndpoint;
        private String apiVersion;
        private Double qualityScore;
        private Boolean isVerified;
        private String originalId; // ID from external API
        private String retrievalMethod; // API, SCRAPING, MANUAL
    }
}
