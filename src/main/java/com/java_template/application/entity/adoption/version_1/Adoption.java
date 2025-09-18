package com.java_template.application.entity.adoption.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;

/**
 * Adoption Entity - Represents the adoption process linking pets and owners
 * 
 * This entity represents the adoption process with application details,
 * adoption completion, and potential return information. The state is 
 * managed via entity metadata workflow.
 */
@Data
public class Adoption implements CyodaEntity {
    public static final String ENTITY_NAME = Adoption.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required relationship fields
    private String petId;
    private String ownerId;
    
    // Application and process dates
    private LocalDate applicationDate;
    private LocalDate adoptionDate;
    
    // Financial information
    private Double adoptionFee;
    
    // Additional information
    private String notes;
    
    // Return information (if applicable)
    private LocalDate returnDate;
    private String returnReason;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields and business rules
        return petId != null && !petId.trim().isEmpty() &&
               ownerId != null && !ownerId.trim().isEmpty() &&
               adoptionFee != null && adoptionFee >= 0.0;
    }
}
