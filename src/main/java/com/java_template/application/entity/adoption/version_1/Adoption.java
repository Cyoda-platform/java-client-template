package com.java_template.application.entity.adoption.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Adoption Entity - Manages the adoption process between pets and potential owners
 * 
 * Purpose: Tracks adoption applications and manages the adoption workflow
 * States: initial_state -> initiated -> under_review -> approved -> completed
 */
@Data
public class Adoption implements CyodaEntity {
    public static final String ENTITY_NAME = Adoption.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String adoptionId;
    
    // Required core business fields
    private String petId;
    private String ownerId;
    private LocalDateTime applicationDate;

    // Optional fields for additional business data
    private LocalDateTime approvalDate;
    private LocalDateTime completionDate;
    private String notes;
    private Double fee;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields according to business rules
        return adoptionId != null && !adoptionId.trim().isEmpty() &&
               petId != null && !petId.trim().isEmpty() &&
               ownerId != null && !ownerId.trim().isEmpty() &&
               applicationDate != null;
    }
}
