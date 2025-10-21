package com.java_template.application.entity.reminder.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: This file contains the Reminder entity that represents scheduled
 * reminders associated with companies in the CRM system.
 */
@Data
public class Reminder implements CyodaEntity {
    public static final String ENTITY_NAME = Reminder.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String reminderId;
    
    // Required core business fields
    private String companyId; // Reference to the company this reminder belongs to
    private String title;
    private LocalDateTime dueDate;
    
    // Optional fields
    private String description;
    private String priority; // e.g., "low", "medium", "high", "urgent"
    private Boolean completed;
    private LocalDateTime completedAt;
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
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return reminderId != null && !reminderId.trim().isEmpty() &&
               companyId != null && !companyId.trim().isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               dueDate != null;
    }
}
