package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * CatFact Entity - Represents a cat fact retrieved from the external API and stored for weekly distribution
 * 
 * Entity States (managed by workflow):
 * - RETRIEVED: Initial state when fact is fetched from API
 * - SCHEDULED: Fact is scheduled for a specific week's email campaign
 * - SENT: Fact has been sent to subscribers
 * - ARCHIVED: Fact is archived after being sent
 */
@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = CatFact.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;
    
    // Required core business fields
    private String fact;
    
    // Optional fields for additional business data
    private Integer length;
    private LocalDateTime retrievedDate;
    private String source;
    private Boolean isUsed;
    private LocalDateTime scheduledDate;

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
        if (fact == null || fact.trim().isEmpty()) {
            return false;
        }
        
        // Validate length matches actual fact text length
        if (length != null && !length.equals(fact.length())) {
            return false;
        }
        
        // Validate retrieved date is not in the future
        if (retrievedDate != null && retrievedDate.isAfter(LocalDateTime.now())) {
            return false;
        }
        
        return true;
    }
}
