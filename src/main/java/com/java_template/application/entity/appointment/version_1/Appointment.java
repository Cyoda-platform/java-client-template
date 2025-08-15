package com.java_template.application.entity.appointment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Appointment implements CyodaEntity {
    public static final String ENTITY_NAME = "Appointment";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // domain identifier
    private String petId; // reference to Pet (serialized UUID)
    private String userId; // reference to User (serialized UUID)
    private String type; // meet, grooming, vet, foster_transfer
    private String scheduledAt; // ISO8601 datetime
    private String status; // REQUESTED, CONFIRMED, COMPLETED, CANCELLED
    private String notes; // optional
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp

    public Appointment() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (type == null || type.isBlank()) return false;
        if (scheduledAt == null || scheduledAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
