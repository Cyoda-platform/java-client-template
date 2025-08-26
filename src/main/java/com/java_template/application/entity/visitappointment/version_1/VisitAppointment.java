package com.java_template.application.entity.visitappointment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class VisitAppointment implements CyodaEntity {
    public static final String ENTITY_NAME = "VisitAppointment"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (UUID as String)
    private String id;
    // Reference to the user scheduling or owning the appointment (UUID as String)
    private String userId;
    // Reference to the pet for the visit (UUID as String)
    private String petId;
    // Appointment datetime in ISO-8601 string
    private String appointmentDateTime;
    // Status as string (e.g., scheduled, cancelled, completed)
    private String status;
    // Optional notes about the appointment
    private String notes;
    // Duration in minutes
    private Integer durationMinutes;
    // Optional location description
    private String location;

    public VisitAppointment() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Required string fields must not be blank
        if (userId == null || userId.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (appointmentDateTime == null || appointmentDateTime.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // durationMinutes, if provided, must be non-negative
        if (durationMinutes != null && durationMinutes < 0) return false;
        return true;
    }
}