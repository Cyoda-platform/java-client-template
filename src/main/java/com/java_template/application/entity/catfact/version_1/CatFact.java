package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * CatFact Entity - Represents cat facts retrieved from the Cat Fact API and stored for email campaigns
 * 
 * Entity State Management:
 * - RETRIEVED: Initial state when fact is fetched from API
 * - READY: Fact is ready to be used in campaigns
 * - USED: Fact has been used in at least one campaign
 * - ARCHIVED: Fact is archived and no longer used
 */
@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = CatFact.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required unique identifier for the cat fact
    private String factId;
    
    // Required fact content
    private String text;
    private Integer length;
    
    // Required metadata
    private LocalDateTime retrievedDate;
    private String source;
    
    // Usage tracking fields
    private Boolean isUsed;
    private Integer usageCount;
    private LocalDateTime lastUsedDate;

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
        if (factId == null || factId.trim().isEmpty()) {
            return false;
        }
        
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        if (length == null || length <= 0) {
            return false;
        }
        
        // Length must match actual text length
        if (!length.equals(text.length())) {
            return false;
        }
        
        if (retrievedDate == null) {
            return false;
        }
        
        // Retrieved date cannot be in the future
        if (retrievedDate.isAfter(LocalDateTime.now())) {
            return false;
        }
        
        if (source == null || source.trim().isEmpty()) {
            return false;
        }
        
        if (isUsed == null) {
            return false;
        }
        
        if (usageCount == null || usageCount < 0) {
            return false;
        }
        
        return true;
    }
}
