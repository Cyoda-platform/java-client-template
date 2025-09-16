package com.java_template.application.entity.eggalarm.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * EggAlarm Entity - Represents an egg cooking alarm
 * 
 * This entity manages the lifecycle of an egg cooking alarm that users can create
 * to time their egg cooking process. It supports different egg types with
 * corresponding cooking times.
 */
@Data
public class EggAlarm implements CyodaEntity {
    public static final String ENTITY_NAME = EggAlarm.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;
    
    // Required core business fields
    private String eggType; // SOFT_BOILED, MEDIUM_BOILED, HARD_BOILED
    private Integer cookingTimeMinutes;
    private LocalDateTime createdAt;
    
    // Optional fields for alarm lifecycle
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String userId; // Optional user identifier for multi-user support

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
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        
        if (eggType == null || eggType.trim().isEmpty()) {
            return false;
        }
        
        // Validate eggType is one of the allowed values
        if (!isValidEggType(eggType)) {
            return false;
        }
        
        if (createdAt == null) {
            return false;
        }
        
        // Validate cookingTimeMinutes if set
        if (cookingTimeMinutes != null && cookingTimeMinutes <= 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates if the egg type is one of the allowed values
     */
    private boolean isValidEggType(String eggType) {
        return "SOFT_BOILED".equals(eggType) || 
               "MEDIUM_BOILED".equals(eggType) || 
               "HARD_BOILED".equals(eggType);
    }
}
