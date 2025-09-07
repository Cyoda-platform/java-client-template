package com.java_template.application.entity.petcareorder.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * PetCareOrder Entity - Represents a pet care service order in the Purrfect Pets system
 * 
 * This entity manages service orders including scheduling, payment,
 * and service delivery tracking.
 */
@Data
public class PetCareOrder implements CyodaEntity {
    public static final String ENTITY_NAME = PetCareOrder.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier
    private String orderId;
    
    // Required core business fields
    private String petId;
    private String ownerId;
    private String serviceType;
    private String serviceDescription;
    private LocalDateTime scheduledDate;
    private Integer duration;
    private Double cost;
    private String paymentMethod;
    
    // Optional fields for additional business data
    private String specialInstructions;
    private String veterinarianName;
    private LocalDateTime orderDate;
    private LocalDateTime completionDate;
    private Integer customerRating;
    private String notes;

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
        return orderId != null && !orderId.trim().isEmpty() &&
               petId != null && !petId.trim().isEmpty() &&
               ownerId != null && !ownerId.trim().isEmpty() &&
               serviceType != null && !serviceType.trim().isEmpty() &&
               serviceDescription != null && !serviceDescription.trim().isEmpty() &&
               scheduledDate != null &&
               duration != null && duration > 0 &&
               cost != null && cost >= 0 &&
               paymentMethod != null && !paymentMethod.trim().isEmpty();
    }
}
